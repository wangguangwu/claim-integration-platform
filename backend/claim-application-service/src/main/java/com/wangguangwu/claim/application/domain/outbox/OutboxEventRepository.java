package com.wangguangwu.claim.application.domain.outbox;

/**
 * Outbox 事件持久化端口。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface OutboxEventRepository {

    /**
     * 保存等待异步投递的事件。
     *
     * @param outboxEvent 待投递事件
     */
    void insert(OutboxEvent outboxEvent);
}
