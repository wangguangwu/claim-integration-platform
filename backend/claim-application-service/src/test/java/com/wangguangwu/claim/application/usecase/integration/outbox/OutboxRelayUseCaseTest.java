package com.wangguangwu.claim.application.usecase.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEvent;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEventPublisher;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEventRepository;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxRelayPolicy;
import com.wangguangwu.claim.application.shared.contract.ApplicationEventEnvelope;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Outbox 投递用例测试：抢占、成功落地、指数退避与失败上限。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class OutboxRelayUseCaseTest {

    private static final String EVENT_ID = "event-1";
    private static final String APPLICATION_NO = "A2026102800000001";
    private static final String TENANT_CODE = "tenant-a";

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final OutboxEventPublisher outboxEventPublisher = mock(OutboxEventPublisher.class);
    private final OutboxRelayPolicy relayPolicy = mock(OutboxRelayPolicy.class);

    private OutboxRelayUseCase outboxRelayUseCase;

    /**
     * 初始化待测投递用例。
     */
    @BeforeEach
    void setUp() {
        when(relayPolicy.batchSize()).thenReturn(50);
        when(relayPolicy.maxRetryCount()).thenReturn(3);
        when(relayPolicy.retryDelay(anyInt())).thenReturn(Duration.ofSeconds(5));
        outboxRelayUseCase = new OutboxRelayUseCase(outboxEventRepository, outboxEventPublisher, relayPolicy,
                new ObjectMapper());
    }

    /**
     * 抢占成功并投递成功后，事件应置为已投递，信封字段取自 Outbox 记录。
     */
    @Test
    void shouldPublishClaimedEventAndMarkPublished() {
        when(outboxEventRepository.findPendingToPublish(any(), eq(50))).thenReturn(List.of(pendingEvent(null)));
        when(outboxEventRepository.claimForPublish(EVENT_ID)).thenReturn(true);

        int publishedCount = outboxRelayUseCase.relayOnce();

        assertThat(publishedCount).isEqualTo(1);
        ArgumentCaptor<ApplicationEventEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(ApplicationEventEnvelope.class);
        verify(outboxEventPublisher).publish(envelopeCaptor.capture());
        ApplicationEventEnvelope envelope = envelopeCaptor.getValue();
        assertThat(envelope.eventId()).isEqualTo(EVENT_ID);
        assertThat(envelope.eventType()).isEqualTo("APPLICATION_CREATED");
        assertThat(envelope.applicationNo()).isEqualTo(APPLICATION_NO);
        assertThat(envelope.traceId()).isEqualTo("trace-1");
        assertThat(envelope.occurredAt()).isNotBlank();
        verify(outboxEventRepository).markPublished(eq(EVENT_ID), any(LocalDateTime.class));
    }

    /**
     * 抢占失败说明其他实例已发送，本次不得重复投递。
     */
    @Test
    void shouldSkipEventWhenClaimFails() {
        when(outboxEventRepository.findPendingToPublish(any(), eq(50))).thenReturn(List.of(pendingEvent(null)));
        when(outboxEventRepository.claimForPublish(EVENT_ID)).thenReturn(false);

        int publishedCount = outboxRelayUseCase.relayOnce();

        assertThat(publishedCount).isZero();
        verify(outboxEventPublisher, never()).publish(any());
        verify(outboxEventRepository, never()).markPublished(anyString(), any());
    }

    /**
     * 首次投递失败按退避基准 5 秒回到待投递。
     */
    @Test
    void shouldScheduleRetryWithBaseBackoffOnFirstFailure() {
        when(outboxEventRepository.findPendingToPublish(any(), eq(50))).thenReturn(List.of(pendingEvent(null)));
        when(outboxEventRepository.claimForPublish(EVENT_ID)).thenReturn(true);
        doThrow(new IllegalStateException("MQ 不可用"))
                .when(outboxEventPublisher).publish(any());

        int publishedCount = outboxRelayUseCase.relayOnce();

        assertThat(publishedCount).isZero();
        ArgumentCaptor<LocalDateTime> nextRetryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxEventRepository).markPendingForRetry(eq(EVENT_ID), eq(1), nextRetryCaptor.capture());
        assertThat(nextRetryCaptor.getValue()).isAfter(LocalDateTime.now().plusSeconds(3));
        verify(outboxEventRepository, never()).markFailed(anyString(), anyInt());
        verify(outboxEventRepository, never()).markPublished(anyString(), any());
    }

    /**
     * 达到最大投递次数后置为失败，不再回到待投递。
     */
    @Test
    void shouldMarkFailedWhenRetryLimitReached() {
        when(outboxEventRepository.findPendingToPublish(any(), eq(50))).thenReturn(List.of(pendingEvent(2)));
        when(outboxEventRepository.claimForPublish(EVENT_ID)).thenReturn(true);
        doThrow(new IllegalStateException("MQ 不可用"))
                .when(outboxEventPublisher).publish(any());

        outboxRelayUseCase.relayOnce();

        verify(outboxEventRepository).markFailed(EVENT_ID, 3);
        verify(outboxEventRepository, never()).markPendingForRetry(anyString(),
                anyInt(), any());
    }

    /**
     * 载荷缺少 traceId 时使用事件标识兜底，保证信封字段不为空。
     */
    @Test
    void shouldUseEventIdWhenPayloadHasNoTraceId() {
        OutboxEvent eventWithoutTrace = OutboxEvent.restore(EVENT_ID, "APPLICATION_CREATED", 1, APPLICATION_NO,
                TENANT_CODE, "{\"applicationNo\":\"" + APPLICATION_NO + "\"}", null, null, null, null);
        when(outboxEventRepository.findPendingToPublish(any(), eq(50))).thenReturn(List.of(eventWithoutTrace));
        when(outboxEventRepository.claimForPublish(EVENT_ID)).thenReturn(true);

        outboxRelayUseCase.relayOnce();

        ArgumentCaptor<ApplicationEventEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(ApplicationEventEnvelope.class);
        verify(outboxEventPublisher).publish(envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().traceId()).isEqualTo(EVENT_ID);
    }

    private OutboxEvent pendingEvent(Integer retryCount) {
        return OutboxEvent.restore(EVENT_ID, "APPLICATION_CREATED", 1, APPLICATION_NO, TENANT_CODE,
                "{\"applicationNo\":\"" + APPLICATION_NO + "\",\"traceId\":\"trace-1\"}", null, retryCount, null,
                null);
    }
}
