package com.wangguangwu.claim.application.interfaces.integration.scheduling;

import com.wangguangwu.claim.application.usecase.integration.outbox.OutboxRelayUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时触发 Outbox 投递，异常只记录日志，不中断后续调度。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxRelayScheduler {

    private final OutboxRelayUseCase outboxRelayUseCase;

    /**
     * 按固定间隔投递到期事件。
     */
    @Scheduled(fixedDelayString = "${claim.outbox.relay.fixed-delay-ms}")
    public void relay() {
        try {
            int publishedCount = outboxRelayUseCase.relayOnce();
            if (publishedCount > 0) {
                log.info("Outbox 事件投递完成，publishedCount={}", publishedCount);
            }
        } catch (RuntimeException exception) {
            log.error("Outbox 事件投递调度异常，等待下一轮调度重试", exception);
        }
    }
}
