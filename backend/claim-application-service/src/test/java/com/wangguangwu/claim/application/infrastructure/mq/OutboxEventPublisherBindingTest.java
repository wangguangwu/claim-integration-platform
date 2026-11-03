package com.wangguangwu.claim.application.infrastructure.mq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.shared.message.AuditDeliveryRequestedMessage;
import com.wangguangwu.claim.application.shared.message.MaterialProcessMessage;
import com.wangguangwu.claim.application.shared.message.MaterialProcessPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;

/**
 * Outbox 出站绑定测试：验证消息具体类型与输出绑定的映射。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("Outbox 出站绑定")
class OutboxEventPublisherBindingTest {

    private final StreamBridge streamBridge = mock(StreamBridge.class);
    private final RocketMqOutboxPublisher publisher = new RocketMqOutboxPublisher(streamBridge);

    @Test
    @DisplayName("材料处理消息发送到 materialProcess-out-0")
    void materialProcessMessage_usesMaterialBinding() {
        when(streamBridge.send(eq("materialProcess-out-0"), any())).thenReturn(true);

        publisher.publish(new MaterialProcessMessage("evt-1", "A2026110200000001", "tenant-a", "trace-1",
                new MaterialProcessPayload(1)));

        verify(streamBridge).send(eq("materialProcess-out-0"), any());
    }

    @Test
    @DisplayName("审核交付消息发送到 auditDelivery-out-0")
    void auditDeliveryMessage_usesAuditDeliveryBinding() {
        when(streamBridge.send(eq("auditDelivery-out-0"), any())).thenReturn(true);

        publisher.publish(new AuditDeliveryRequestedMessage("evt-2", "A2026110200000001", "tenant-a", "trace-2"));

        verify(streamBridge).send(eq("auditDelivery-out-0"), any());
    }

    @Test
    @DisplayName("发送失败时抛出带绑定名的异常")
    void sendFailure_throwsIllegalStateException() {
        when(streamBridge.send(eq("auditDelivery-out-0"), any())).thenReturn(false);

        assertThatThrownBy(() -> publisher.publish(
                new AuditDeliveryRequestedMessage("evt-3", "A2026110200000001", "tenant-a", "trace-3")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auditDelivery-out-0")
                .hasMessageContaining("evt-3");
    }
}
