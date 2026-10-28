package com.wangguangwu.claim.application.domain.interaction;

/**
 * 交互事件持久化端口。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface InteractionLogRepository {

    /**
     * 保存一条已发生的交互事件。
     *
     * @param interactionLog 交互事件
     */
    void insert(InteractionLog interactionLog);
}
