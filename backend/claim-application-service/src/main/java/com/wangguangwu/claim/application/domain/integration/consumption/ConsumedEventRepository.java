package com.wangguangwu.claim.application.domain.integration.consumption;

/**
 * 消息消费登记端口，按消费者组与事件标识去重。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface ConsumedEventRepository {

    /**
     * 判断事件是否已被同一消费者组处理。
     *
     * @param consumerGroup 消费者组
     * @param eventId       事件标识
     * @return 已处理时为 true
     */
    boolean exists(String consumerGroup, String eventId);

    /**
     * 登记一次消费；唯一键冲突表示该事件已被处理。
     *
     * @param consumerGroup 消费者组
     * @param eventId       事件标识
     * @param claimApplicationNo 平台申请编号
     * @return 首次登记时为 true
     */
    boolean register(String consumerGroup, String eventId, String claimApplicationNo);
}
