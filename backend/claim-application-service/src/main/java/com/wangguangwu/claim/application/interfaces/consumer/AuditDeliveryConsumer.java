package com.wangguangwu.claim.application.interfaces.consumer;

import com.wangguangwu.claim.application.shared.message.AuditDeliveryRequestedMessage;
import com.wangguangwu.claim.application.application.process.AuditDeliveryTransactionService;
import com.wangguangwu.claim.application.application.process.AuditDeliveryUseCase;
import com.wangguangwu.claim.application.application.command.AuditDeliveryCommand;
import com.wangguangwu.claim.foundation.common.logging.MdcContext;
import com.wangguangwu.claim.foundation.common.logging.MdcKeys;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * 审核交付 Topic 的消费者，只做消息装配，业务处理交给用例。
 *
 * <p>消费异常只在此处记录一条 ERROR 后原样抛出，交由 binder 重试；重投耗尽后由 broker 转入死信主题，
 * 重复消息由消费登记保证幂等。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
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
    public Consumer<Message<AuditDeliveryRequestedMessage>> auditDelivery() {
        return message -> {
            AuditDeliveryRequestedMessage event = message.getPayload();
            try {
                bindEventContext(event);
                auditDeliveryUseCase.deliver(toCommand(event));
            } catch (RuntimeException exception) {
                logConsumeFailure(event, exception);
                throw exception;
            } finally {
                MdcContext.clear();
            }
        };
    }

    /**
     * 把消息信封中的链路与租户标识写入 MDC，链路标识缺失时使用事件标识。
     *
     * @param event 审核交付消息
     */
    private void bindEventContext(AuditDeliveryRequestedMessage event) {
        String traceId = event.traceId();
        MdcContext.put(MdcKeys.TRACE_ID, traceId == null || traceId.isBlank() ? event.eventId() : traceId);
        MdcContext.put(MdcKeys.TENANT_CODE, event.tenantCode());
        MdcContext.put(MdcKeys.EVENT_ID, event.eventId());
    }

    private AuditDeliveryCommand toCommand(AuditDeliveryRequestedMessage event) {
        return new AuditDeliveryCommand(event.claimApplicationNo(), event.tenantCode(), event.eventId(),
                event.traceId());
    }

    /**
     * 记录消费失败的定位信息，只打印异常摘要，堆栈由本地实现与 binder 输出，避免同一异常重复打印。
     *
     * @param event     审核交付消息，消息本身异常时可能为 null
     * @param exception 消费异常
     */
    private void logConsumeFailure(AuditDeliveryRequestedMessage event, RuntimeException exception) {
        log.error("审核交付消费失败，交由 MQ 重投，consumerGroup={}，claimApplicationNo={}，eventId={}，reason={}",
                AuditDeliveryTransactionService.CONSUMER_GROUP,
                event == null ? null : event.claimApplicationNo(),
                event == null ? null : event.eventId(),
                exception.toString());
    }
}
