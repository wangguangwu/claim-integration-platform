package com.wangguangwu.claim.application.interfaces.audit.messaging;

import com.wangguangwu.claim.application.shared.contract.ClaimApplicationEventEnvelope;
import com.wangguangwu.claim.application.usecase.audit.AuditDeliveryUseCase;
import com.wangguangwu.claim.application.usecase.audit.command.AuditDeliveryCommand;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * 审核交付 Topic 的消费者，只做信封解析，业务处理交给用例。
 *
 * <p>消费异常不在此处捕获，交由 binder 重试；重投耗尽后由 broker 转入死信主题，
 * 重复消息由消费登记保证幂等。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
public class AuditDeliveryConsumer {

    private final AuditDeliveryUseCase auditDeliveryUseCase;

    /**
     * 声明审核交付消费函数，绑定名与配置一致。
     *
     * @return 消息消费函数
     */
    @Bean
    public Consumer<Message<ClaimApplicationEventEnvelope>> auditDelivery() {
        return message -> auditDeliveryUseCase.deliver(toCommand(message.getPayload()));
    }

    private AuditDeliveryCommand toCommand(ClaimApplicationEventEnvelope envelope) {
        return new AuditDeliveryCommand(envelope.claimApplicationNo(), envelope.tenantCode(), envelope.eventId(),
                envelope.traceId());
    }
}
