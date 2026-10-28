package com.wangguangwu.claim.application.domain.outbox;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 按创建时间升序查询可投递事件。
     *
     * @param publishBefore 允许投递的时间上限，用于过滤未到重试时间的事件
     * @param limit         最大条数
     * @return 待投递事件
     */
    List<OutboxEvent> findPendingToPublish(LocalDateTime publishBefore, int limit);

    /**
     * 把待投递事件抢占为发送中，抢占失败表示已被其他实例处理。
     *
     * @param eventId 事件标识
     * @return 抢占成功时为 true
     */
    boolean claimForPublish(String eventId);

    /**
     * 记录投递成功。
     *
     * @param eventId     事件标识
     * @param publishedAt 投递成功时间
     */
    void markPublished(String eventId, LocalDateTime publishedAt);

    /**
     * 记录投递失败并把事件放回待投递状态。
     *
     * @param eventId     事件标识
     * @param retryCount  已重试次数
     * @param nextRetryAt 下次允许重试的时间
     */
    void markPendingForRetry(String eventId, int retryCount, LocalDateTime nextRetryAt);

    /**
     * 超过最大重试次数后标记为投递失败，保留记录供人工处理。
     *
     * @param eventId    事件标识
     * @param retryCount 已重试次数
     */
    void markFailed(String eventId, int retryCount);

    /**
     * 把停留过久的发送中事件重置为待投递，处理投递进程崩溃场景。
     *
     * @param sendingBefore 发送中状态的停留截止时间
     * @return 重置条数
     */
    int resetStuckSending(LocalDateTime sendingBefore);
}
