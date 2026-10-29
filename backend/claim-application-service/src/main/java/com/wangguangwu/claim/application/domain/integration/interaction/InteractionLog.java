package com.wangguangwu.claim.application.domain.integration.interaction;

import java.time.LocalDateTime;

/**
 * 一次系统交互中的单个已发生事件。
 *
 * <p>交互事件的装配知识只留在本域：方向、目标系统和结果状态一律由 {@link InteractionType} 与
 * {@link InteractionAction} 决定，调用方只描述"谁、在哪一步、是否失败"。</p>
 *
 * @param applicationNo    所属理赔申请的平台申请编号
 * @param interactionNo    一次交互行为的聚合编号
 * @param interactionType  交互业务类型
 * @param action           当前事件动作
 * @param direction        交互方向
 * @param targetSystem     目标外部系统或内部组件
 * @param requestId        调用方提供的幂等请求标识
 * @param traceId          链路追踪标识
 * @param resultStatus     交互结果状态
 * @param errorCode        失败时的错误码
 * @param errorMessage     失败时的脱敏错误说明
 * @param occurredAt       事件实际发生时间
 * @author wangguangwu
 * @since 2026-10-27
 */
public record InteractionLog(
        String applicationNo,
        String interactionNo,
        String interactionType,
        String action,
        String direction,
        String targetSystem,
        String requestId,
        String traceId,
        String resultStatus,
        String errorCode,
        String errorMessage,
        LocalDateTime occurredAt) {

    /**
     * 交互表错误说明列宽，超长部分按列宽截断，避免写入失败。
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    /**
     * 按交互模型装配一条交互事件。
     *
     * @param interactionType 交互业务类型，决定方向与目标系统
     * @param applicationNo   平台申请编号
     * @param interactionNo   行为组编号，同一行为的开始与结束事件共用
     * @param action          阶段动作，决定结果状态
     * @param requestId       请求标识
     * @param traceId         链路标识
     * @param errorCode       失败错误码，非失败场景为 null
     * @param errorMessage    失败说明，非失败场景为 null
     * @param occurredAt      事件实际发生时间
     * @return 交互事件
     */
    public static InteractionLog of(InteractionType interactionType, String applicationNo, String interactionNo,
                                   InteractionAction action, String requestId, String traceId, String errorCode,
                                   String errorMessage, LocalDateTime occurredAt) {
        return new InteractionLog(applicationNo, interactionNo, interactionType.name(), action.name(),
                interactionType.direction().name(), interactionType.targetSystem(), requestId, traceId,
                action.resultStatus().name(), errorCode, truncate(errorMessage), occurredAt);
    }

    private static String truncate(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
