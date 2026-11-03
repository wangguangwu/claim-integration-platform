package com.wangguangwu.claim.application.application.process.compensation;

import com.wangguangwu.claim.application.application.port.ClaimApplicationCompensationPolicy;
import com.wangguangwu.claim.application.application.port.OutboxEventRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbox 投递滞留补偿：把停留过久的发送中事件重置为待投递。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxSendingCompensationHandler {

    private final OutboxEventRepository outboxEventRepository;
    private final ClaimApplicationCompensationPolicy compensationPolicy;

    /**
     * 处理投递进程崩溃后遗留的发送中事件。
     *
     * @return 重置条数
     */
    public int reset() {
        LocalDateTime sendingBefore = CompensationDefaults.now().minus(compensationPolicy.outboxSendingTimeout());
        int resetCount = outboxEventRepository.resetStuckSending(sendingBefore);
        if (resetCount > 0) {
            log.info("Outbox 发送滞留事件已重置为待投递，resetCount={}，sendingBefore={}", resetCount,
                    sendingBefore);
        }
        return resetCount;
    }
}
