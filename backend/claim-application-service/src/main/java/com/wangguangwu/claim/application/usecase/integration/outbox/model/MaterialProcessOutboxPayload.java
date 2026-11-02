package com.wangguangwu.claim.application.usecase.integration.outbox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 材料处理事件的 Outbox 存储载荷。
 *
 * <p>消息顶层已经承载平台申请编号、租户编码与链路标识，存储层只保留组装消息所需的字段。</p>
 *
 * @param claimApplicationAttempt 事件产生时的申请尝试次数
 * @param traceId                 链路标识
 * @author wangguangwu
 * @since 2026-11-02
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaterialProcessOutboxPayload(Integer claimApplicationAttempt, String traceId) {
}
