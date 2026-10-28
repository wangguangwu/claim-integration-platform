package com.wangguangwu.claim.application.infrastructure.messaging;

import com.wangguangwu.claim.application.usecase.messaging.ApplicationEventEnvelope;
import com.wangguangwu.claim.application.usecase.messaging.ApplicationEventType;
import com.wangguangwu.claim.application.usecase.messaging.OutboxEventPublisher;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 基于 StreamBridge 的 Outbox 事件投递实现，按事件类型选择输出绑定。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class RocketMqOutboxPublisher implements OutboxEventPublisher {

    private final StreamBridge streamBridge;

    public RocketMqOutboxPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    /**
     * 发送信封；绑定发送失败时抛出异常，交由投递用例记录退避重试。
     *
     * @param envelope 消息信封
     */
    @Override
    public void publish(ApplicationEventEnvelope envelope) {
        String bindingName = ApplicationEventType.from(envelope.eventType()).bindingName();
        Message<ApplicationEventEnvelope> message = MessageBuilder.withPayload(envelope).build();
        if (!streamBridge.send(bindingName, message)) {
            throw new IllegalStateException("Outbox 事件发送失败，未找到可用的消息绑定，bindingName=" + bindingName
                    + ", eventId=" + envelope.eventId());
        }
    }
}
