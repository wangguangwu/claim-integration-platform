package com.wangguangwu.claim.application.interfaces.consumer;

import com.wangguangwu.claim.application.shared.message.MaterialProcessMessage;
import com.wangguangwu.claim.application.shared.message.MaterialProcessPayload;
import com.wangguangwu.claim.application.application.process.MaterialProcessingTransactionService;
import com.wangguangwu.claim.application.application.process.MaterialProcessingUseCase;
import com.wangguangwu.claim.application.application.command.MaterialProcessCommand;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * 材料处理 Topic 的消费者，只做消息装配，业务处理交给用例。
 *
 * <p>消费异常只在此处记录一条 ERROR 后原样抛出，交由 binder 重试；重复消息由消费登记与条件更新保证幂等。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
public class MaterialProcessConsumer {

    private final MaterialProcessingUseCase materialProcessingUseCase;

    /**
     * 声明材料处理消费函数，绑定名与 002 冻结的配置一致。
     *
     * @return 消息消费函数
     */
    @Bean
    public Consumer<Message<MaterialProcessMessage>> materialProcess() {
        return message -> {
            MaterialProcessMessage event = message.getPayload();
            try {
                materialProcessingUseCase.process(toCommand(event));
            } catch (RuntimeException exception) {
                logConsumeFailure(event, exception);
                throw exception;
            }
        };
    }

    private MaterialProcessCommand toCommand(MaterialProcessMessage event) {
        MaterialProcessPayload payload = event.payload();
        Integer claimApplicationAttempt = payload == null ? null : payload.claimApplicationAttempt();
        if (claimApplicationAttempt == null) {
            throw new IllegalArgumentException("材料处理事件缺少申请尝试次数，eventId=" + event.eventId());
        }
        return new MaterialProcessCommand(event.claimApplicationNo(), event.tenantCode(), event.eventId(),
                event.traceId(), claimApplicationAttempt);
    }

    /**
     * 记录消费失败的定位信息，只打印异常摘要，堆栈由本地实现与 binder 输出，避免同一异常重复打印。
     *
     * @param event     材料处理消息，消息本身异常时可能为 null
     * @param exception 消费异常
     */
    private void logConsumeFailure(MaterialProcessMessage event, RuntimeException exception) {
        log.error("材料处理消费失败，交由 MQ 重投，consumerGroup={}，claimApplicationNo={}，eventId={}，reason={}",
                MaterialProcessingTransactionService.CONSUMER_GROUP,
                event == null ? null : event.claimApplicationNo(),
                event == null ? null : event.eventId(),
                exception.toString());
    }
}
