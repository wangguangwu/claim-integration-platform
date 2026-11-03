package com.wangguangwu.claim.application.application.process;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.shared.message.AuditDeliveryRequestedMessage;
import com.wangguangwu.claim.application.shared.message.MaterialProcessMessage;
import com.wangguangwu.claim.application.shared.message.MaterialProcessPayload;
import com.wangguangwu.claim.application.shared.message.OutboundClaimApplicationEventMessage;
import com.wangguangwu.claim.application.application.port.model.AuditDeliveryRequestedOutboxPayload;
import com.wangguangwu.claim.application.application.port.model.ClaimApplicationEventType;
import com.wangguangwu.claim.application.application.port.model.MaterialProcessOutboxPayload;
import com.wangguangwu.claim.application.application.port.model.OutboxEvent;
import com.wangguangwu.claim.application.application.port.OutboxEventPublisher;
import com.wangguangwu.claim.application.application.port.OutboxEventRepository;
import com.wangguangwu.claim.application.application.port.OutboxRelayPolicy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Outbox 投递用例：抢占事件、组装具体消息、按结果推进投递状态。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class OutboxRelayUseCase {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final OutboxRelayPolicy outboxRelayPolicy;
    private final ObjectMapper objectMapper;

    /**
     * 投递一批到期事件，单条失败不影响同批其他事件。
     *
     * @return 本次投递成功的事件数
     */
    public int relayOnce() {
        LocalDateTime publishBefore = LocalDateTime.now(BUSINESS_ZONE_ID);
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingToPublish(publishBefore,
                outboxRelayPolicy.batchSize());
        int publishedCount = 0;
        for (OutboxEvent pendingEvent : pendingEvents) {
            if (publish(pendingEvent)) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    private boolean publish(OutboxEvent outboxEvent) {
        if (!outboxEventRepository.claimForPublish(outboxEvent.eventId())) {
            return false;
        }
        try {
            outboxEventPublisher.publish(toMessage(outboxEvent));
            outboxEventRepository.markPublished(outboxEvent.eventId(), LocalDateTime.now(BUSINESS_ZONE_ID));
            return true;
        } catch (RuntimeException exception) {
            recordPublishFailure(outboxEvent, exception);
            return false;
        }
    }

    /**
     * 投递失败按指数退避回到待投递；达到最大投递次数后置为失败，等人工处理。
     *
     * @param outboxEvent 投递失败的事件
     * @param exception   投递异常
     */
    private void recordPublishFailure(OutboxEvent outboxEvent, RuntimeException exception) {
        int retryCount = currentRetryCount(outboxEvent) + 1;
        if (retryCount >= outboxRelayPolicy.maxRetryCount()) {
            outboxEventRepository.markFailed(outboxEvent.eventId(), retryCount);
            log.error("Outbox 事件投递次数超限，标记失败，eventId={}, eventType={}, claimApplicationNo={}, retryCount={}",
                    outboxEvent.eventId(), outboxEvent.eventType(), outboxEvent.claimApplicationNo(), retryCount,
                    exception);
            return;
        }
        LocalDateTime nextRetryAt = LocalDateTime.now(BUSINESS_ZONE_ID).plus(outboxRelayPolicy.retryDelay(retryCount));
        outboxEventRepository.markPendingForRetry(outboxEvent.eventId(), retryCount, nextRetryAt);
        log.warn("Outbox 事件投递失败，等待退避重试，eventId={}, eventType={}, claimApplicationNo={}, retryCount={}, "
                        + "nextRetryAt={}", outboxEvent.eventId(), outboxEvent.eventType(), outboxEvent.claimApplicationNo(),
                retryCount, nextRetryAt, exception);
    }

    private int currentRetryCount(OutboxEvent outboxEvent) {
        return outboxEvent.retryCount() == null ? 0 : outboxEvent.retryCount();
    }

    /**
     * 按事件类型把存储载荷转换为具体消息。
     */
    private OutboundClaimApplicationEventMessage toMessage(OutboxEvent outboxEvent) {
        ClaimApplicationEventType eventType = ClaimApplicationEventType.from(outboxEvent.eventType());
        return switch (eventType) {
            case CLAIM_APPLICATION_CREATED, CLAIM_APPLICATION_REAPPLIED -> toMaterialProcessMessage(outboxEvent);
            case AUDIT_DELIVERY_REQUESTED -> toAuditDeliveryRequestedMessage(outboxEvent);
        };
    }

    private MaterialProcessMessage toMaterialProcessMessage(OutboxEvent outboxEvent) {
        MaterialProcessOutboxPayload storedPayload = readPayload(outboxEvent, MaterialProcessOutboxPayload.class);
        return new MaterialProcessMessage(outboxEvent.eventId(), outboxEvent.claimApplicationNo(),
                outboxEvent.tenantCode(), traceId(outboxEvent, storedPayload.traceId()),
                new MaterialProcessPayload(storedPayload.claimApplicationAttempt()));
    }

    private AuditDeliveryRequestedMessage toAuditDeliveryRequestedMessage(OutboxEvent outboxEvent) {
        AuditDeliveryRequestedOutboxPayload storedPayload = readPayload(outboxEvent,
                AuditDeliveryRequestedOutboxPayload.class);
        return new AuditDeliveryRequestedMessage(outboxEvent.eventId(), outboxEvent.claimApplicationNo(),
                outboxEvent.tenantCode(), traceId(outboxEvent, storedPayload.traceId()));
    }

    private <T> T readPayload(OutboxEvent outboxEvent, Class<T> payloadType) {
        try {
            return objectMapper.readValue(outboxEvent.payload(), payloadType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outbox 事件载荷不合法，eventId=" + outboxEvent.eventId(), exception);
        }
    }

    private String traceId(OutboxEvent outboxEvent, String traceId) {
        return traceId == null ? outboxEvent.eventId() : traceId;
    }
}
