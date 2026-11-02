package com.wangguangwu.claim.application.infrastructure.integration.local;

import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionType;
import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 外部系统本地实现的公共基类，封装模拟耗时与中断恢复逻辑。
 *
 * <p>子类只需传入 {@link InteractionType} 与 {@link LocalIntegrationProperties}，
 * 并在业务方法中调用 {@link #pause(String)} 即可复用休眠与日志逻辑。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractLocalIntegration {

    /** 不需要申请编号的端口传此占位符，保持日志口径一致。 */
    protected static final String CLAIM_APPLICATION_NO_PLACEHOLDER = "-";

    private final InteractionType interactionType;
    private final LocalIntegrationProperties properties;

    /**
     * 模拟外部系统调用耗时；延迟为零时直接返回，休眠被中断时恢复中断标记并抛异常。
     *
     * @param claimApplicationNo 平台申请编号；不需要时传 {@link #CLAIM_APPLICATION_NO_PLACEHOLDER}
     */
    protected void pause(String claimApplicationNo) {
        Duration delay = properties.delay();
        log.info("外部系统本地实现调用，targetSystem={}，interactionType={}，claimApplicationNo={}，delay={}",
                interactionType.targetSystem(), interactionType, claimApplicationNo, delay);
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

    /**
     * 获取交互类型，供子类业务方法使用。
     *
     * @return 交互类型
     */
    protected InteractionType interactionType() {
        return interactionType;
    }

    /**
     * 获取本地集成配置，供子类业务方法使用。
     *
     * @return 本地集成配置
     */
    protected LocalIntegrationProperties properties() {
        return properties;
    }
}
