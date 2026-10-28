package com.wangguangwu.claim.application.domain.outbox;

import java.time.LocalDateTime;

/**
 * 与本地业务事务一致提交的待投递事件。
 *
 * @param eventId      事件标识，全局唯一
 * @param eventType    事件类型
 * @param eventVersion 事件版本
 * @param applicationNo 平台申请编号
 * @param tenantCode   租户编码
 * @param payload      不含敏感信息的业务载荷
 * @param status       投递状态
 * @param retryCount   已重试次数
 * @param nextRetryAt  下次允许重试的时间
 * @param createdAt    事件创建时间
 * @author wangguangwu
 * @since 2026-10-27
 */
public record OutboxEvent(
        String eventId,
        String eventType,
        int eventVersion,
        String applicationNo,
        String tenantCode,
        String payload,
        OutboxEventStatus status,
        Integer retryCount,
        LocalDateTime nextRetryAt,
        LocalDateTime createdAt) {

    /**
     * 构建等待投递的新事件。
     *
     * @param eventId        事件标识
     * @param eventType      事件类型
     * @param eventVersion   事件版本
     * @param applicationNo  平台申请编号
     * @param tenantCode     租户编码
     * @param payload        业务载荷
     * @return 待投递事件
     */
    public static OutboxEvent pending(String eventId, String eventType, int eventVersion, String applicationNo,
                                      String tenantCode, String payload) {
        return new OutboxEvent(eventId, eventType, eventVersion, applicationNo, tenantCode, payload,
                OutboxEventStatus.PENDING, null, null, null);
    }
}
