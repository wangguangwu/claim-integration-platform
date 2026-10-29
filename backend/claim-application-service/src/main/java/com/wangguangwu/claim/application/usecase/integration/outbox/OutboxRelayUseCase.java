package com.wangguangwu.claim.application.usecase.integration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEvent;
import com.wangguangwu.claim.application.domain.integration.outbox.port.OutboxEventPublisher;
import com.wangguangwu.claim.application.domain.integration.outbox.port.OutboxEventRepository;
import com.wangguangwu.claim.application.domain.integration.outbox.port.OutboxRelayPolicy;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationEventEnvelope;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Outbox 投递用例：抢占事件、组装信封、按结果推进投递状态。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class OutboxRelayUseCase {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OCCURRED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final String TRACE_ID_FIELD = "traceId";

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
            outboxEventPublisher.publish(toEnvelope(outboxEvent));
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

    private ClaimApplicationEventEnvelope toEnvelope(OutboxEvent outboxEvent) {
        JsonNode payload = readPayload(outboxEvent);
        return new ClaimApplicationEventEnvelope(outboxEvent.eventId(), outboxEvent.eventType(),
                outboxEvent.eventVersion(), outboxEvent.claimApplicationNo(), outboxEvent.tenantCode(),
                formatOccurredAt(outboxEvent), traceId(outboxEvent, payload), toPayloadMap(payload));
    }

    private Map<String, Object> toPayloadMap(JsonNode payload) {
        try {
            return objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {
            });
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Outbox 事件载荷不是 JSON 对象", exception);
        }
    }

    private JsonNode readPayload(OutboxEvent outboxEvent) {
        try {
            return objectMapper.readTree(outboxEvent.payload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outbox 事件载荷不是合法 JSON，eventId=" + outboxEvent.eventId(), exception);
        }
    }

    /**
     * 事件发生时间取自落库时间；内存构造的事件没有该字段时使用当前时间。
     *
     * @param outboxEvent 待投递事件
     * @return 带时区偏移的时间文本
     */
    private String formatOccurredAt(OutboxEvent outboxEvent) {
        ZonedDateTime occurredAt = outboxEvent.createdAt() == null
                ? ZonedDateTime.now(BUSINESS_ZONE_ID)
                : outboxEvent.createdAt().atZone(BUSINESS_ZONE_ID);
        return occurredAt.format(OCCURRED_AT_FORMATTER);
    }

    private String traceId(OutboxEvent outboxEvent, JsonNode payload) {
        JsonNode traceIdNode = payload.get(TRACE_ID_FIELD);
        return traceIdNode == null || traceIdNode.isNull() ? outboxEvent.eventId() : traceIdNode.asText();
    }
}
