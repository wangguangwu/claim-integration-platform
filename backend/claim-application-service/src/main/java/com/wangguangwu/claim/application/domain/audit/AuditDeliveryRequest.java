package com.wangguangwu.claim.application.domain.audit;

/**
 * 审核交付入参，只携带交付所需的非敏感业务标识。
 *
 * @param claimApplicationNo 平台申请编号
 * @param tenantCode         租户编码
 * @param traceId            链路标识，随交付请求透传以便外部系统串联日志
 * @author wangguangwu
 * @since 2026-10-29
 */
public record AuditDeliveryRequest(String claimApplicationNo, String tenantCode, String traceId) {
}
