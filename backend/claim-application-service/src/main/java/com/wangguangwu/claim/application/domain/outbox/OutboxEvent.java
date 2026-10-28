package com.wangguangwu.claim.application.domain.outbox;

/**
 * 与本地业务事务一致提交的待投递事件。
 *
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
        String status) {
}
