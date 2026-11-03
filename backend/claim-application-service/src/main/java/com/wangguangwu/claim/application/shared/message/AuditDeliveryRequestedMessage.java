package com.wangguangwu.claim.application.shared.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 审核交付消息，通知本服务把已具备审核条件的申请交付审核系统。
 *
 * <p>业务键只有平台申请编号，没有二级业务载荷。</p>
 *
 * @param eventId            事件标识
 * @param claimApplicationNo 平台申请编号
 * @param tenantCode         租户编码
 * @param traceId            链路标识
 * @author wangguangwu
 * @since 2026-11-02
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditDeliveryRequestedMessage(
        String eventId,
        String claimApplicationNo,
        String tenantCode,
        String traceId) implements OutboundClaimApplicationEventMessage {
}
