package com.wangguangwu.claim.application.interfaces.material.messaging;

import com.wangguangwu.claim.application.shared.contract.ApplicationEventEnvelope;
import com.wangguangwu.claim.application.usecase.material.MaterialProcessingUseCase;
import com.wangguangwu.claim.application.usecase.material.command.MaterialProcessCommand;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * 材料处理 Topic 的消费者，只做信封解析，业务处理交给用例。
 *
 * <p>消费异常不在此处捕获，交由 binder 重试；重复消息由消费登记与条件更新保证幂等。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
public class MaterialProcessConsumer {

    private static final String APPLICATION_ATTEMPT_FIELD = "applicationAttempt";

    private final MaterialProcessingUseCase materialProcessingUseCase;

    /**
     * 声明材料处理消费函数，绑定名与 002 冻结的配置一致。
     *
     * @return 消息消费函数
     */
    @Bean
    public Consumer<Message<ApplicationEventEnvelope>> materialProcess() {
        return message -> materialProcessingUseCase.process(toCommand(message.getPayload()));
    }

    private MaterialProcessCommand toCommand(ApplicationEventEnvelope envelope) {
        Map<String, Object> payload = envelope.payload();
        Object applicationAttempt = payload == null ? null : payload.get(APPLICATION_ATTEMPT_FIELD);
        if (!(applicationAttempt instanceof Number attemptNumber)) {
            throw new IllegalArgumentException("材料处理事件缺少申请尝试次数，eventId=" + envelope.eventId());
        }
        return new MaterialProcessCommand(envelope.applicationNo(), envelope.tenantCode(), envelope.eventId(),
                envelope.traceId(), attemptNumber.intValue());
    }
}
