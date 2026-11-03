package com.wangguangwu.claim.application.application.port;

import com.wangguangwu.claim.application.shared.message.OutboundClaimApplicationEventMessage;

/**
 * Outbox 事件投递端口，由基础设施按消息具体类型路由到对应 Topic。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface OutboxEventPublisher {

    /**
     * 投递一条出站消息；投递未成功时必须抛出异常，由投递用例记录退避重试。
     *
     * @param message 出站消息
     */
    void publish(OutboundClaimApplicationEventMessage message);
}
