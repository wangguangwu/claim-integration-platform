package com.wangguangwu.claim.application.infrastructure.integration.persistence.outbox;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEvent;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEventRepository;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEventStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的 Outbox 事件仓储实现。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Repository
public class MyBatisOutboxEventRepository implements OutboxEventRepository {

    private static final int INITIAL_RETRY_COUNT = 0;

    private final OutboxEventDOMapper outboxEventMapper;

    /**
     * 保存初始为待投递状态的 Outbox 事件。
     *
     * @param outboxEvent 待投递事件
     */
    @Override
    public void insert(OutboxEvent outboxEvent) {
        OutboxEventDO outboxEventDO = OutboxEventDO.builder().eventId(outboxEvent.eventId())
                .eventType(outboxEvent.eventType()).eventVersion(outboxEvent.eventVersion())
                .applicationNo(outboxEvent.applicationNo()).tenantCode(outboxEvent.tenantCode())
                .payload(outboxEvent.payload()).status(outboxEvent.status().name())
                .retryCount(INITIAL_RETRY_COUNT).build();
        outboxEventMapper.insert(outboxEventDO);
    }

    /**
     * 按创建时间升序查询已到投递时间的待投递事件。
     *
     * @param publishBefore 允许投递的时间上限
     * @param limit         最大条数
     * @return 待投递事件
     */
    @Override
    public List<OutboxEvent> findPendingToPublish(LocalDateTime publishBefore, int limit) {
        Page<OutboxEventDO> page = new Page<>(1L, limit, false);
        return outboxEventMapper.selectPage(page, Wrappers.lambdaQuery(OutboxEventDO.class)
                        .eq(OutboxEventDO::getStatus, OutboxEventStatus.PENDING.name())
                        .and(wrapper -> wrapper.isNull(OutboxEventDO::getNextRetryAt)
                                .or()
                                .le(OutboxEventDO::getNextRetryAt, publishBefore))
                        .orderByAsc(OutboxEventDO::getCreatedAt))
                .getRecords().stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * 把待投递事件抢占为发送中，避免多实例重复发送。
     *
     * @param eventId 事件标识
     * @return 抢占成功时为 true
     */
    @Override
    public boolean claimForPublish(String eventId) {
        return outboxEventMapper.update(null, Wrappers.lambdaUpdate(OutboxEventDO.class)
                .eq(OutboxEventDO::getEventId, eventId)
                .eq(OutboxEventDO::getStatus, OutboxEventStatus.PENDING.name())
                .set(OutboxEventDO::getStatus, OutboxEventStatus.SENDING.name())) == 1;
    }

    /**
     * 记录投递成功时间。
     *
     * @param eventId     事件标识
     * @param publishedAt 投递成功时间
     */
    @Override
    public void markPublished(String eventId, LocalDateTime publishedAt) {
        outboxEventMapper.update(null, Wrappers.lambdaUpdate(OutboxEventDO.class)
                .eq(OutboxEventDO::getEventId, eventId)
                .eq(OutboxEventDO::getStatus, OutboxEventStatus.SENDING.name())
                .set(OutboxEventDO::getStatus, OutboxEventStatus.PUBLISHED.name())
                .set(OutboxEventDO::getPublishedAt, publishedAt));
    }

    /**
     * 记录投递失败并把事件放回待投递状态。
     *
     * @param eventId     事件标识
     * @param retryCount  已重试次数
     * @param nextRetryAt 下次允许重试的时间
     */
    @Override
    public void markPendingForRetry(String eventId, int retryCount, LocalDateTime nextRetryAt) {
        outboxEventMapper.update(null, Wrappers.lambdaUpdate(OutboxEventDO.class)
                .eq(OutboxEventDO::getEventId, eventId)
                .eq(OutboxEventDO::getStatus, OutboxEventStatus.SENDING.name())
                .set(OutboxEventDO::getStatus, OutboxEventStatus.PENDING.name())
                .set(OutboxEventDO::getRetryCount, retryCount)
                .set(OutboxEventDO::getNextRetryAt, nextRetryAt));
    }

    /**
     * 超限后标记投递失败，保留记录供人工处理。
     *
     * @param eventId    事件标识
     * @param retryCount 已重试次数
     */
    @Override
    public void markFailed(String eventId, int retryCount) {
        outboxEventMapper.update(null, Wrappers.lambdaUpdate(OutboxEventDO.class)
                .eq(OutboxEventDO::getEventId, eventId)
                .eq(OutboxEventDO::getStatus, OutboxEventStatus.SENDING.name())
                .set(OutboxEventDO::getStatus, OutboxEventStatus.FAILED.name())
                .set(OutboxEventDO::getRetryCount, retryCount));
    }

    /**
     * 把停留过久的发送中事件重置为待投递，处理投递进程崩溃场景。
     *
     * @param sendingBefore 发送中状态的停留截止时间
     * @return 重置条数
     */
    @Override
    public int resetStuckSending(LocalDateTime sendingBefore) {
        return outboxEventMapper.update(null, Wrappers.lambdaUpdate(OutboxEventDO.class)
                .eq(OutboxEventDO::getStatus, OutboxEventStatus.SENDING.name())
                .le(OutboxEventDO::getUpdatedAt, sendingBefore)
                .set(OutboxEventDO::getStatus, OutboxEventStatus.PENDING.name()));
    }

    private OutboxEvent toDomain(OutboxEventDO outboxEventDO) {
        return OutboxEvent.restore(outboxEventDO.getEventId(), outboxEventDO.getEventType(),
                outboxEventDO.getEventVersion(), outboxEventDO.getApplicationNo(), outboxEventDO.getTenantCode(),
                outboxEventDO.getPayload(), OutboxEventStatus.valueOf(outboxEventDO.getStatus()),
                outboxEventDO.getRetryCount(), outboxEventDO.getNextRetryAt(), outboxEventDO.getCreatedAt());
    }
}
