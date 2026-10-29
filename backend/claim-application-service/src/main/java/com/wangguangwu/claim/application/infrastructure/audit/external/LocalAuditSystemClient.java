package com.wangguangwu.claim.application.infrastructure.audit.external;

import com.wangguangwu.claim.application.domain.audit.AuditDeliveryRequest;
import com.wangguangwu.claim.application.domain.audit.ServiceAuditSystemPort;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审核系统交付端口的本地实现：按配置休眠并打印交互日志，开启失败开关时抛出异常。
 *
 * <p>抛出异常表示审核系统不可用，交付事务不会开启，消息重投后可重新执行。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LocalAuditSystemClient implements ServiceAuditSystemPort {

    private static final InteractionType INTERACTION_TYPE = InteractionType.AUDIT_DELIVERY;

    private final LocalIntegrationProperties properties;

    /**
     * 交付申请给审核系统；本地实现不发起外部请求，失败开关开启时抛出异常。
     *
     * @param request 交付入参
     */
    @Override
    public void deliver(AuditDeliveryRequest request) {
        pause(request.claimApplicationNo());
        if (properties.failAuditDelivery()) {
            log.error("外部系统本地实现交付失败，targetSystem={}，claimApplicationNo={}", INTERACTION_TYPE.targetSystem(),
                    request.claimApplicationNo());
            throw new IllegalStateException("审核系统本地实现交付失败，claimApplicationNo=" + request.claimApplicationNo());
        }
    }

    private void pause(String claimApplicationNo) {
        Duration delay = properties.delay();
        log.info("外部系统本地实现调用，targetSystem={}，interactionType={}，claimApplicationNo={}，delay={}",
                INTERACTION_TYPE.targetSystem(), INTERACTION_TYPE, claimApplicationNo, delay);
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("外部系统本地实现休眠被中断，claimApplicationNo=" + claimApplicationNo,
                    exception);
        }
    }
}
