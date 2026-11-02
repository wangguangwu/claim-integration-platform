package com.wangguangwu.claim.application.usecase.integration.outbox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 审核交付事件的 Outbox 存储载荷。
 *
 * <p>审核交付消息没有业务载荷，存储层仅保留链路标识供投递调度组装消息。</p>
 *
 * @param traceId 链路标识
 * @author wangguangwu
 * @since 2026-11-02
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditDeliveryRequestedOutboxPayload(String traceId) {
}
