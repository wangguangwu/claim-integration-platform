package com.wangguangwu.claim.application.application.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.shared.message.AuditDeliveryRequestedMessage;
import com.wangguangwu.claim.application.shared.message.MaterialProcessMessage;
import com.wangguangwu.claim.application.shared.message.OutboundClaimApplicationEventMessage;
import com.wangguangwu.claim.application.application.port.model.OutboxEvent;
import com.wangguangwu.claim.application.application.port.model.OutboxEventStatus;
import com.wangguangwu.claim.application.application.port.OutboxEventPublisher;
import com.wangguangwu.claim.application.application.port.OutboxEventRepository;
import com.wangguangwu.claim.application.application.port.OutboxRelayPolicy;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Outbox 投递调度测试：验证存储载荷到具体消息的转换与失败处理。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("Outbox 投递调度")
class OutboxRelayUseCaseTest {

    private static final String EVENT_ID = "evt-1";

    private OutboxEventRepository outboxEventRepository;
    private OutboxEventPublisher outboxEventPublisher;
    private OutboxRelayPolicy outboxRelayPolicy;
    private OutboxRelayUseCase outboxRelayUseCase;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        outboxRelayPolicy = mock(OutboxRelayPolicy.class);
        when(outboxRelayPolicy.batchSize()).thenReturn(10);
        when(outboxRelayPolicy.maxRetryCount()).thenReturn(3);
        when(outboxRelayPolicy.retryDelay(anyInt())).thenReturn(Duration.ofSeconds(1));
        outboxRelayUseCase = new OutboxRelayUseCase(outboxEventRepository, outboxEventPublisher, outboxRelayPolicy,
                new ObjectMapper());
    }

    @Test
    @DisplayName("材料处理事件转换为类型化消息")
    void materialProcessEvent_convertsToTypedMessage() {
        givenPendingEvent("CLAIM_APPLICATION_CREATED", "{\"claimApplicationAttempt\":2,\"traceId\":\"trace-1\"}");

        outboxRelayUseCase.relayOnce();

        assertPublished(MaterialProcessMessage.class, message -> {
            assertThat(message.claimApplicationNo()).isEqualTo("A2026110200000001");
            assertThat(message.tenantCode()).isEqualTo("tenant-a");
            assertThat(message.traceId()).isEqualTo("trace-1");
            assertThat(message.payload().claimApplicationAttempt()).isEqualTo(2);
        });
        verify(outboxEventRepository).markPublished(eq(EVENT_ID), any());
    }

    @Test
    @DisplayName("重新申请事件与首次签收共用材料处理消息")
    void reappliedEvent_convertsToMaterialProcessMessage() {
        givenPendingEvent("CLAIM_APPLICATION_REAPPLIED", "{\"claimApplicationAttempt\":3,\"traceId\":\"trace-2\"}");

        outboxRelayUseCase.relayOnce();

        assertPublished(MaterialProcessMessage.class, message ->
                assertThat(message.payload().claimApplicationAttempt()).isEqualTo(3));
    }

    @Test
    @DisplayName("审核交付事件转换为 4 字段消息")
    void auditDeliveryEvent_convertsToFourFieldMessage() {
        givenPendingEvent("AUDIT_DELIVERY_REQUESTED", "{\"traceId\":\"trace-3\"}");

        outboxRelayUseCase.relayOnce();

        assertPublished(AuditDeliveryRequestedMessage.class, message -> {
            assertThat(message.claimApplicationNo()).isEqualTo("A2026110200000001");
            assertThat(message.tenantCode()).isEqualTo("tenant-a");
            assertThat(message.traceId()).isEqualTo("trace-3");
        });
    }

    @Test
    @DisplayName("链路标识缺失时回退事件标识")
    void missingTraceId_fallsBackToEventId() {
        givenPendingEvent("AUDIT_DELIVERY_REQUESTED", "{}");

        outboxRelayUseCase.relayOnce();

        assertPublished(AuditDeliveryRequestedMessage.class, message ->
                assertThat(message.traceId()).isEqualTo(EVENT_ID));
    }

    @Test
    @DisplayName("材料处理缺少申请尝试次数时保留 null")
    void materialProcessMissingAttempt_keepsNull() {
        givenPendingEvent("CLAIM_APPLICATION_CREATED", "{\"traceId\":\"trace-4\"}");

        outboxRelayUseCase.relayOnce();

        assertPublished(MaterialProcessMessage.class, message ->
                assertThat(message.payload().claimApplicationAttempt()).isNull());
    }

    @Test
    @DisplayName("存储载荷非法 JSON：记录重试且不投递")
    void invalidPayload_recordsRetry() {
        givenPendingEvent("CLAIM_APPLICATION_CREATED", "not-json");

        outboxRelayUseCase.relayOnce();

        verify(outboxEventPublisher, never()).publish(any());
        verify(outboxEventRepository).markPendingForRetry(eq(EVENT_ID), eq(1), any());
    }

    @Test
    @DisplayName("申请尝试次数类型不符：记录重试且不投递")
    void invalidAttemptType_recordsRetry() {
        givenPendingEvent("CLAIM_APPLICATION_CREATED", "{\"claimApplicationAttempt\":\"two\",\"traceId\":\"trace-5\"}");

        outboxRelayUseCase.relayOnce();

        verify(outboxEventPublisher, never()).publish(any());
        verify(outboxEventRepository).markPendingForRetry(eq(EVENT_ID), eq(1), any());
    }

    @Test
    @DisplayName("未知事件类型：记录重试且不投递")
    void unknownEventType_recordsRetry() {
        givenPendingEvent("UNKNOWN_EVENT", "{}");

        outboxRelayUseCase.relayOnce();

        verify(outboxEventPublisher, never()).publish(any());
        verify(outboxEventRepository).markPendingForRetry(eq(EVENT_ID), eq(1), any());
    }

    private void givenPendingEvent(String eventType, String payload) {
        OutboxEvent outboxEvent = new OutboxEvent(EVENT_ID, eventType, 1, "A2026110200000001", "tenant-a", payload,
                OutboxEventStatus.PENDING, 0, null, null);
        when(outboxEventRepository.findPendingToPublish(any(), anyInt())).thenReturn(List.of(outboxEvent));
        when(outboxEventRepository.claimForPublish(EVENT_ID)).thenReturn(true);
    }

    private <T extends OutboundClaimApplicationEventMessage> void assertPublished(Class<T> messageType,
                                                                                 Consumer<T> assertions) {
        ArgumentCaptor<OutboundClaimApplicationEventMessage> captor =
                ArgumentCaptor.forClass(OutboundClaimApplicationEventMessage.class);
        verify(outboxEventPublisher).publish(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(messageType);
        assertions.accept(messageType.cast(captor.getValue()));
    }
}
