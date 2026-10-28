package com.wangguangwu.claim.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.application.Application;
import com.wangguangwu.claim.application.domain.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.interaction.InteractionLog;
import com.wangguangwu.claim.application.domain.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.outbox.OutboxEvent;
import com.wangguangwu.claim.application.usecase.messaging.ApplicationEventType;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 构建异步链路的交互事件与审核交付事件，不访问数据库。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Service
public class ApplicationLifecycleRecordFactory {

    private static final String DIRECTION_INBOUND = "INBOUND";
    private static final String DIRECTION_OUTBOUND = "OUTBOUND";
    private static final String MATERIAL_TARGET_SYSTEM = "MATERIAL_PROCESSOR";
    private static final String UNDERWRITING_TARGET_SYSTEM = "UNDERWRITING_SYSTEM";
    private static final String RESULT_STARTED = "STARTED";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILED = "FAILED";
    private static final String TRACE_ID_FIELD = "traceId";
    private static final int EVENT_VERSION = 1;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper objectMapper;

    public ApplicationLifecycleRecordFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
     * @param application 已进入等待审核签收的申请
     * @param traceId     链路标识
     * @return 待投递事件
     */
    public OutboxEvent auditDeliveryRequestedEvent(Application application, String traceId) {
        Map<String, Object> payload = Map.of(
                "applicationNo", application.getApplicationNo(),
                "tenantCode", application.getTenantCode(),
                "applicationAttempt", application.getApplicationAttempt(),
                TRACE_ID_FIELD, traceId);
        return OutboxEvent.pending(UUID.randomUUID().toString(),
                ApplicationEventType.AUDIT_DELIVERY_REQUESTED.name(), EVENT_VERSION,
                application.getApplicationNo(), application.getTenantCode(), serialize(payload));
    }

    /**
     * 构建一条交互事件，方向与外部系统由交互类型决定。
     *
     * @param interactionType 交互类型
     * @param applicationNo   平台申请编号
     * @param interactionNo   行为组编号
     * @param action          阶段动作
     * @param requestId       请求标识
     * @param traceId         链路标识
     * @param errorCode       失败错误码，非失败场景为 null
     * @param errorMessage    失败说明，非失败场景为 null
     * @return 交互事件
     */
    public InteractionLog interaction(InteractionType interactionType, String applicationNo, String interactionNo,
                                      InteractionAction action, String requestId, String traceId,
                                      String errorCode, String errorMessage) {
        return new InteractionLog(applicationNo, interactionNo, interactionType.name(), action.name(),
                direction(interactionType), targetSystem(interactionType), requestId, traceId, resultStatus(action),
                errorCode, truncateErrorMessage(errorMessage), now());
    }

    /**
     * 外部异常信息可能很长，按交互表列宽截断，避免写入失败。
     */
    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String direction(InteractionType interactionType) {
        return interactionType == InteractionType.UNDERWRITING ? DIRECTION_OUTBOUND : DIRECTION_INBOUND;
    }

    private String targetSystem(InteractionType interactionType) {
        return interactionType == InteractionType.MATERIAL_PROCESS ? MATERIAL_TARGET_SYSTEM
                : UNDERWRITING_TARGET_SYSTEM;
    }

    private String resultStatus(InteractionAction action) {
        return switch (action) {
            case STARTED -> RESULT_STARTED;
            case COMPLETED, REUSED -> RESULT_SUCCESS;
            case FAILED -> RESULT_FAILED;
        };
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
