package com.wangguangwu.claim.application.application.port.model;

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

    /** 事件待投递，可被投递任务抢占。 */
    PENDING,

    /** 事件已被某个投递任务抢占，等待发送结果回写。 */
    SENDING,

    /** 事件已成功发布到消息中间件，不再重复投递。 */
    PUBLISHED,

    /** 事件发送达到失败终态，需要人工排查或补偿。 */
    FAILED
}
