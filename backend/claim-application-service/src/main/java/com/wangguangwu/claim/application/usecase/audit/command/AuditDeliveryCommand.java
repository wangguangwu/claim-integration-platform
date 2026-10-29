package com.wangguangwu.claim.application.usecase.audit.command;

/**
 * 审核交付命令，来自审核交付 Topic 的消息信封。
 *
 * @param claimApplicationNo 平台申请编号
 * @param tenantCode         租户编码
 * @param eventId            事件标识，同时作为交互行为组的请求标识
 * @param traceId            链路标识
 * @author wangguangwu
 * @since 2026-10-29
 */
public record AuditDeliveryCommand(String claimApplicationNo, String tenantCode, String eventId, String traceId) {
}
