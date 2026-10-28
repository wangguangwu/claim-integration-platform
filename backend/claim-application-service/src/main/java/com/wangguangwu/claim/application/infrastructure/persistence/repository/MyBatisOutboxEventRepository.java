package com.wangguangwu.claim.application.infrastructure.persistence.repository;

import com.wangguangwu.claim.application.domain.outbox.OutboxEvent;
import com.wangguangwu.claim.application.domain.outbox.OutboxEventRepository;
import com.wangguangwu.claim.application.infrastructure.persistence.dataobject.OutboxEventDO;
import com.wangguangwu.claim.application.infrastructure.persistence.mapper.OutboxEventDOMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的 Outbox 事件仓储实现。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Repository
public class MyBatisOutboxEventRepository implements OutboxEventRepository {

    private static final int INITIAL_RETRY_COUNT = 0;

    private final OutboxEventDOMapper outboxEventMapper;

    public MyBatisOutboxEventRepository(OutboxEventDOMapper outboxEventMapper) {
        this.outboxEventMapper = outboxEventMapper;
    }

    /**
     * 保存初始为待投递状态的 Outbox 事件。
     *
     * @param outboxEvent 待投递事件
     */
    @Override
    public void insert(OutboxEvent outboxEvent) {
        OutboxEventDO outboxEventDO = new OutboxEventDO();
        outboxEventDO.setEventId(outboxEvent.eventId());
        outboxEventDO.setEventType(outboxEvent.eventType());
        outboxEventDO.setEventVersion(outboxEvent.eventVersion());
        outboxEventDO.setApplicationNo(outboxEvent.applicationNo());
        outboxEventDO.setTenantCode(outboxEvent.tenantCode());
        outboxEventDO.setPayload(outboxEvent.payload());
        outboxEventDO.setStatus(outboxEvent.status());
        outboxEventDO.setRetryCount(INITIAL_RETRY_COUNT);
        outboxEventMapper.insert(outboxEventDO);
    }
}
