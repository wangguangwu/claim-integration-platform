package com.wangguangwu.claim.application.shared.contract.message;

/**
 * 理赔申请异步消息的公共契约，只声明三类消费者都实际使用的字段。
 *
 * <p>消息类型集合由密封接口封闭，新增消息必须同时补齐生产、消费与测试。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public sealed interface ClaimApplicationEventMessage
        permits OutboundClaimApplicationEventMessage, AuditSignedMessage {

    /**
     * 事件标识，同时作为消费幂等键与交互轨迹的请求标识。
     *
     * @return 事件标识
     */
    String eventId();

    /**
     * 平台申请编号。
     *
     * @return 平台申请编号
     */
    String claimApplicationNo();

    /**
     * 租户编码。
     *
     * @return 租户编码
     */
    String tenantCode();

    /**
     * 链路标识。
     *
     * @return 链路标识
     */
    String traceId();
}
