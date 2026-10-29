package com.wangguangwu.claim.application.domain.integration.outbox;

/**
 * Outbox 事件的投递状态。
 *
 * <p>{@code SENDING} 是投递前的抢占状态，用于避免多实例重复发送；
 * 发送结果只允许把 {@code SENDING} 推进到 {@code PUBLISHED}、回到 {@code PENDING} 或置为 {@code FAILED}。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum OutboxEventStatus {

    PENDING,
    SENDING,
    PUBLISHED,
    FAILED
}
