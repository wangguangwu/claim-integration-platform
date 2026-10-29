package com.wangguangwu.claim.application.infrastructure.integration.config;

import com.wangguangwu.claim.application.domain.integration.outbox.port.OutboxRelayPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox 投递策略的配置适配器，退避按 base × 2^(n-1) 递增并受单次上限约束。
 *
 * <p>调度间隔由 {@code @Scheduled} 占位符直接读取，不经过领域策略端口。</p>
 *
 * @param fixedDelayMs     两次投递调度的间隔
 * @param batchSize        单次投递处理的最大事件数
 * @param maxRetryCount    允许的最大投递次数，达到后置为失败
 * @param retryBaseSeconds 退避基准秒数
 * @param retryMaxSeconds  单次退避等待上限
 * @author wangguangwu
 * @since 2026-10-27
 */
@ConfigurationProperties(prefix = "claim.outbox.relay")
public record OutboxRelayProperties(
        long fixedDelayMs,
        int batchSize,
        int maxRetryCount,
        long retryBaseSeconds,
        long retryMaxSeconds) implements OutboxRelayPolicy {

    private static final int MAX_BACKOFF_EXPONENT = 30;

    /**
     * 第 n 次投递失败等待 base × 2^(n-1) 秒，累计次数超过移位安全位后按上限截断。
     *
     * @param attemptCount 失败后的累计投递次数
     * @return 退避时长
     */
    @Override
    public Duration retryDelay(int attemptCount) {
        int exponent = Math.min(attemptCount - 1, MAX_BACKOFF_EXPONENT);
        Duration backoff = Duration.ofSeconds(retryBaseSeconds << exponent);
        Duration ceiling = Duration.ofSeconds(retryMaxSeconds);
        return backoff.compareTo(ceiling) > 0 ? ceiling : backoff;
    }
}
