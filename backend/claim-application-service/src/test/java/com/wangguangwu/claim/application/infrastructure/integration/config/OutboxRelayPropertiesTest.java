package com.wangguangwu.claim.application.infrastructure.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.wangguangwu.claim.application.domain.integration.outbox.OutboxRelayPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Outbox 投递策略适配器测试，覆盖指数退避的增长、上限截断与移位安全位。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
class OutboxRelayPropertiesTest {

    private static final long RETRY_BASE_SECONDS = 5L;
    private static final long RETRY_MAX_SECONDS = 60L;

    private final OutboxRelayPolicy relayPolicy = new OutboxRelayProperties(2000L, 50, 3, RETRY_BASE_SECONDS,
            RETRY_MAX_SECONDS);

    /**
     * 第 n 次失败等待 base × 2^(n-1) 秒。
     */
    @Test
    void shouldDoubleBackoffOnEachAttempt() {
        assertThat(relayPolicy.retryDelay(1)).isEqualTo(Duration.ofSeconds(RETRY_BASE_SECONDS));
        assertThat(relayPolicy.retryDelay(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(relayPolicy.retryDelay(4)).isEqualTo(Duration.ofSeconds(40));
    }

    /**
     * 退避超过配置上限时按上限截断，累计次数极大时移位结果也不越界。
     */
    @Test
    void shouldCapBackoffAtConfiguredCeiling() {
        assertThat(relayPolicy.retryDelay(5)).isEqualTo(Duration.ofSeconds(RETRY_MAX_SECONDS));
        assertThat(relayPolicy.retryDelay(100)).isEqualTo(Duration.ofSeconds(RETRY_MAX_SECONDS));
    }
}
