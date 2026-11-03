package com.wangguangwu.claim.application.shared.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 材料处理消息，触发首次签收或重新申请后的材料处理。
 *
 * @param eventId            事件标识
 * @param claimApplicationNo 平台申请编号
 * @param tenantCode         租户编码
 * @param traceId            链路标识
 * @param payload            材料处理载荷
 * @author wangguangwu
 * @since 2026-11-02
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaterialProcessMessage(
        String eventId,
        String claimApplicationNo,
        String tenantCode,
        String traceId,
        MaterialProcessPayload payload) implements OutboundClaimApplicationEventMessage {
}
