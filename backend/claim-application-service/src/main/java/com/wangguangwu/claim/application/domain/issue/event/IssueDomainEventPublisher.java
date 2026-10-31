package com.wangguangwu.claim.application.domain.issue.event;

/**
 * 问题件领域事件发布端口，由基础设施实现事件投递方式。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
public interface IssueDomainEventPublisher {

    /**
     * 发布一个已发生的问题件领域事实。
     *
     * @param event 领域事件
     */
    void publish(IssueDomainEvent event);
}
