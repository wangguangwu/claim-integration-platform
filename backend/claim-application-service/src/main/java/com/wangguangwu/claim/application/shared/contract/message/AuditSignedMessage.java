package com.wangguangwu.claim.application.shared.contract.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 审核签收消息，审核系统回传的事实通知。
 *
 * <p>eventType 保留用于校验外部系统发送的事件类型必须为 AUDIT_SIGNED。</p>
 *
 * @param eventId            事件标识
 * @param eventType          事件类型
 * @param claimApplicationNo 平台申请编号
 * @param tenantCode         租户编码
 * @param traceId            链路标识
 * @param payload            审核签收载荷
 * @author wangguangwu
 * @since 2026-11-02
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditSignedMessage(
        String eventId,
        String eventType,
        String claimApplicationNo,
        String tenantCode,
        String traceId,
        AuditSignedPayload payload) implements ClaimApplicationEventMessage {
}
