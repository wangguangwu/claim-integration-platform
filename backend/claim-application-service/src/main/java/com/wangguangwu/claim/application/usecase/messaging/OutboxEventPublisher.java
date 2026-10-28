package com.wangguangwu.claim.application.usecase.messaging;

/**
 * Outbox 事件投递端口，由基础设施按事件类型路由到对应 Topic。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface OutboxEventPublisher {

    /**
     * 投递一条事件；投递未成功时必须抛出异常，由投递用例记录退避重试。
     *
     * @param envelope 消息信封
     */
    void publish(ApplicationEventEnvelope envelope);
}
