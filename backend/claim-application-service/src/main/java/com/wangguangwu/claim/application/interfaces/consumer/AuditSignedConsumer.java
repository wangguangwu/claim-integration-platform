package com.wangguangwu.claim.application.interfaces.consumer;

import com.wangguangwu.claim.application.shared.message.AuditSignedMessage;
import com.wangguangwu.claim.application.application.process.AuditSignedTransactionService;
import com.wangguangwu.claim.application.application.process.AuditSignedUseCase;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * 审核签收 Topic 的消费者，只做消息装配，业务处理交给用例。
 *
 * <p>消费异常只在此处记录一条 ERROR 后原样抛出，交由 binder 重试；重投耗尽后由 broker 转入死信主题，
 * 重复消息由消费登记保证幂等。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Slf4j
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
public class AuditSignedConsumer {

    private final AuditSignedMessageParser messageParser;
    private final AuditSignedMessageAssembler messageAssembler;
    private final AuditSignedUseCase auditSignedUseCase;

    /**
     * 声明审核签收消费函数，绑定名与配置一致。
     *
     * <p>报文按原生字节接收，由解析器完成严格反序列化，避免 binder 复合转换器降级到宽松的 fastjson。</p>
     *
     * @return 消息消费函数
     */
    @Bean
    public Consumer<Message<byte[]>> auditSigned() {
        return message -> {
            AuditSignedMessage event = null;
            try {
                event = messageParser.parse(message.getPayload());
                auditSignedUseCase.record(messageAssembler.toCommand(event));
            } catch (RuntimeException exception) {
                logConsumeFailure(event, exception);
                throw exception;
            }
        };
    }

    /**
     * 记录消费失败的定位信息，只打印异常摘要，堆栈由 binder 输出，避免同一异常重复打印。
     *
     * @param event     审核签收消息，消息本身异常时可能为 null
     * @param exception 消费异常
     */
    private void logConsumeFailure(AuditSignedMessage event, RuntimeException exception) {
        log.error("审核签收消费失败，交由 MQ 重投，consumerGroup={}，claimApplicationNo={}，eventId={}，reason={}",
                AuditSignedTransactionService.CONSUMER_GROUP,
                event == null ? null : event.claimApplicationNo(),
                event == null ? null : event.eventId(),
                exception.toString());
    }
}
