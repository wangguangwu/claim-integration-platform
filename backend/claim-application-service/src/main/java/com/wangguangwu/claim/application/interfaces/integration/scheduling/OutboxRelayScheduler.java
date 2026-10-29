package com.wangguangwu.claim.application.interfaces.integration.scheduling;

import com.wangguangwu.claim.application.usecase.integration.outbox.OutboxRelayUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时触发 Outbox 投递，异常只记录日志，不中断后续调度。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelayUseCase outboxRelayUseCase;

    public OutboxRelayScheduler(OutboxRelayUseCase outboxRelayUseCase) {
        this.outboxRelayUseCase = outboxRelayUseCase;
    }

    /**
     * 按固定间隔投递到期事件。
     */
    @Scheduled(fixedDelayString = "${claim.outbox.relay.fixed-delay-ms}")
    public void relay() {
        try {
            int publishedCount = outboxRelayUseCase.relayOnce();
            if (publishedCount > 0) {
                LOGGER.info("Outbox 事件投递完成，publishedCount={}", publishedCount);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Outbox 事件投递调度异常，等待下一轮调度重试", exception);
        }
    }
}
