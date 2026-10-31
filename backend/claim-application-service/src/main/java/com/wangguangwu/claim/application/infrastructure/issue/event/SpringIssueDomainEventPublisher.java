package com.wangguangwu.claim.application.infrastructure.issue.event;

import com.wangguangwu.claim.application.domain.issue.event.IssueDomainEvent;
import com.wangguangwu.claim.application.domain.issue.event.IssueDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 问题件领域事件发布端口的进程内实现，把领域事实转交 Spring 事件总线。
 *
 * <p>领域层只认端口，后续切换为 Outbox 或消息队列时只需替换本实现。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@RequiredArgsConstructor
@Component
public class SpringIssueDomainEventPublisher implements IssueDomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 发布领域事件。
     *
     * @param event 领域事件
     */
    @Override
    public void publish(IssueDomainEvent event) {
        eventPublisher.publishEvent(event);
    }
}
