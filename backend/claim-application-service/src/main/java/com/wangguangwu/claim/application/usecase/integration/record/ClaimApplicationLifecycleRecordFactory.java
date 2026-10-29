package com.wangguangwu.claim.application.usecase.integration.record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLog;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.integration.outbox.ClaimApplicationEventType;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEvent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 构建异步链路的交互事件与审核交付事件，不访问数据库。
 *
 * <p>本类只负责事件载荷序列化与标识生成，交互方向和结果状态等领域词汇由 {@code domain/integration} 决定。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationLifecycleRecordFactory {

    private static final String TRACE_ID_FIELD = "traceId";
    private static final int EVENT_VERSION = 1;
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper objectMapper;

    /**
     * 生成行为组编号，同一行为的开始与结束事件共用该编号。
     *
     * @return 行为组编号
     */
    public String newInteractionNo() {
        return UUID.randomUUID().toString();
    }

    /**
     * 构建审核交付事件，载荷不包含人员敏感信息。
     *
     * @param claimApplication 已进入等待审核签收的申请
     * @param traceId     链路标识
     * @return 待投递事件
     */
    public OutboxEvent auditDeliveryRequestedEvent(ClaimApplication claimApplication, String traceId) {
        Map<String, Object> payload = Map.of(
                "claimApplicationNo", claimApplication.getClaimApplicationNo(),
                "tenantCode", claimApplication.getTenantCode(),
                "claimApplicationAttempt", claimApplication.getClaimApplicationAttempt(),
                TRACE_ID_FIELD, traceId);
        return newOutboxEvent(claimApplication, ClaimApplicationEventType.AUDIT_DELIVERY_REQUESTED, payload);
    }

    /**
     * 构建首次签收事件，触发材料处理，载荷不包含人员敏感信息。
     *
     * @param claimApplication 已完成签收的申请
     * @param traceId     链路标识
     * @return 待投递事件
     */
    public OutboxEvent claimApplicationCreatedEvent(ClaimApplication claimApplication, String traceId) {
        return claimApplicationProcessEvent(claimApplication, ClaimApplicationEventType.CLAIM_APPLICATION_CREATED, traceId);
    }

    /**
     * 构建重新申请事件，触发材料重新处理，载荷不包含人员敏感信息。
     *
     * @param claimApplication 已重新申请的申请
     * @param traceId     链路标识
     * @return 待投递事件
     */
    public OutboxEvent claimApplicationReappliedEvent(ClaimApplication claimApplication, String traceId) {
        return claimApplicationProcessEvent(claimApplication, ClaimApplicationEventType.CLAIM_APPLICATION_REAPPLIED, traceId);
    }

    /**
     * 构建一条交互事件。
     *
     * @param interactionType 交互类型
     * @param claimApplicationNo   平台申请编号
     * @param interactionNo   行为组编号
     * @param action          阶段动作
     * @param requestId       请求标识
     * @param traceId         链路标识
     * @param errorCode       失败错误码，非失败场景为 null
     * @param errorMessage    失败说明，非失败场景为 null
     * @return 交互事件
     */
    public InteractionLog interaction(InteractionType interactionType, String claimApplicationNo, String interactionNo,
                                      InteractionAction action, String requestId, String traceId,
                                      String errorCode, String errorMessage) {
        return InteractionLog.of(interactionType, claimApplicationNo, interactionNo, action, requestId, traceId,
                errorCode, errorMessage, now());
    }

    /**
     * 签收与重新申请事件需要把事件类型也写进载荷，供消费侧自校验。
     */
    private OutboxEvent claimApplicationProcessEvent(ClaimApplication claimApplication, ClaimApplicationEventType eventType,
                                                String traceId) {
        Map<String, Object> payload = Map.of(
                "claimApplicationNo", claimApplication.getClaimApplicationNo(),
                "tenantCode", claimApplication.getTenantCode(),
                "eventType", eventType.name(),
                "claimApplicationAttempt", claimApplication.getClaimApplicationAttempt(),
                TRACE_ID_FIELD, traceId);
        return newOutboxEvent(claimApplication, eventType, payload);
    }

    private OutboxEvent newOutboxEvent(ClaimApplication claimApplication, ClaimApplicationEventType eventType,
                                       Map<String, Object> payload) {
        return OutboxEvent.pending(UUID.randomUUID().toString(), eventType.name(), EVENT_VERSION,
                claimApplication.getClaimApplicationNo(), claimApplication.getTenantCode(), serialize(payload));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("异步记录序列化失败", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE_ID);
    }
}
