package com.wangguangwu.claim.application.infrastructure.mq;

import com.wangguangwu.claim.application.shared.message.AuditDeliveryRequestedMessage;
import com.wangguangwu.claim.application.shared.message.MaterialProcessMessage;
import com.wangguangwu.claim.application.shared.message.OutboundClaimApplicationEventMessage;
import com.wangguangwu.claim.application.application.port.model.ClaimApplicationEventType;
import com.wangguangwu.claim.application.application.port.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 基于 StreamBridge 的 Outbox 事件投递实现，按消息具体类型选择输出绑定。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Component
public class RocketMqOutboxPublisher implements OutboxEventPublisher {

    private final StreamBridge streamBridge;

    /**
     * 发送消息；绑定发送失败时抛出异常，交由投递用例记录退避重试。
     *
     * @param message 出站消息
     */
    @Override
    public void publish(OutboundClaimApplicationEventMessage message) {
        String bindingName = bindingName(message);
        Message<OutboundClaimApplicationEventMessage> streamMessage = MessageBuilder.withPayload(message).build();
        if (!streamBridge.send(bindingName, streamMessage)) {
            throw new IllegalStateException("Outbox 事件发送失败，未找到可用的消息绑定，bindingName=" + bindingName
                    + ", eventId=" + message.eventId());
        }
    }

    /**
     * 材料处理的首次签收与重新申请共用同一输出绑定。
     */
    private String bindingName(OutboundClaimApplicationEventMessage message) {
        return switch (message) {
            case MaterialProcessMessage ignored -> ClaimApplicationEventType.CLAIM_APPLICATION_CREATED.bindingName();
            case AuditDeliveryRequestedMessage ignored ->
                    ClaimApplicationEventType.AUDIT_DELIVERY_REQUESTED.bindingName();
        };
    }
}
