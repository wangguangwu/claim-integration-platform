package com.wangguangwu.claim.application.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wangguangwu.claim.application.domain.consumption.ConsumedEventRepository;
import com.wangguangwu.claim.application.infrastructure.persistence.dataobject.ConsumedEventDO;
import com.wangguangwu.claim.application.infrastructure.persistence.mapper.ConsumedEventDOMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的消息消费登记实现，依赖唯一键完成去重。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Repository
public class MyBatisConsumedEventRepository implements ConsumedEventRepository {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ConsumedEventDOMapper consumedEventMapper;

    public MyBatisConsumedEventRepository(ConsumedEventDOMapper consumedEventMapper) {
        this.consumedEventMapper = consumedEventMapper;
    }

    /**
     * 判断事件是否已在同一消费者组登记。
     *
     * @param consumerGroup 消费者组
     * @param eventId       事件标识
     * @return 已处理时为 true
     */
    @Override
    public boolean exists(String consumerGroup, String eventId) {
        return consumedEventMapper.exists(Wrappers.lambdaQuery(ConsumedEventDO.class)
                .eq(ConsumedEventDO::getConsumerGroup, consumerGroup)
                .eq(ConsumedEventDO::getEventId, eventId));
    }

    /**
     * 登记一次消费，唯一键冲突说明该事件已被并发消费者处理。
     *
     * @param consumerGroup 消费者组
     * @param eventId       事件标识
     * @param applicationNo 平台申请编号
     * @return 首次登记时为 true
     */
    @Override
    public boolean register(String consumerGroup, String eventId, String applicationNo) {
        ConsumedEventDO consumedEventDO = new ConsumedEventDO();
        consumedEventDO.setConsumerGroup(consumerGroup);
        consumedEventDO.setEventId(eventId);
        consumedEventDO.setApplicationNo(applicationNo);
        consumedEventDO.setConsumedAt(LocalDateTime.now(BUSINESS_ZONE_ID));
        try {
            consumedEventMapper.insert(consumedEventDO);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }
}
