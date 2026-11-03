package com.wangguangwu.claim.application.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.wangguangwu.claim.application.infrastructure.config.ClaimApplicationRedisCircuitProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Redis 熔断单元测试：验证三态转换与恢复标记。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("Redis 熔断")
class RedisAvailabilityCircuitTest {

    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(10);
    private static final Duration LOG_INTERVAL = Duration.ofSeconds(10);

    private RedisAvailabilityCircuit circuit;

    @BeforeEach
    void setUp() {
        ClaimApplicationRedisCircuitProperties properties = new ClaimApplicationRedisCircuitProperties(
                FAILURE_THRESHOLD, OPEN_DURATION, LOG_INTERVAL);
        circuit = new RedisAvailabilityCircuit(properties);
    }

    @Test
    @DisplayName("关闭态 tryAcquire() 返回 true")
    void tryAcquire_closedState_returnsTrue() {
        assertThat(circuit.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("连续失败达到阈值后打开，tryAcquire() 返回 false")
    void tryAcquire_afterThresholdFailures_returnsFalse() {
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            circuit.recordFailure();
        }

        assertThat(circuit.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("半开态探测成功关闭并置恢复标记")
    void recordSuccess_halfOpenState_closesAndSetsRecoveryRequired() throws InterruptedException {
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            circuit.recordFailure();
        }
        Thread.sleep(OPEN_DURATION.toMillis() + 100);

        circuit.tryAcquire();
        circuit.recordSuccess();

        assertThat(circuit.tryAcquire()).isTrue();
        assertThat(circuit.consumeRecoveryRequired()).isTrue();
        assertThat(circuit.consumeRecoveryRequired()).isFalse();
    }

    @Test
    @DisplayName("半开态探测失败重新打开")
    void recordFailure_halfOpenState_reopens() throws InterruptedException {
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            circuit.recordFailure();
        }
        Thread.sleep(OPEN_DURATION.toMillis() + 100);

        circuit.tryAcquire();
        circuit.recordFailure();

        assertThat(circuit.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("探测超时后重置允许新探测")
    void tryAcquire_probeExpired_allowsNewProbe() throws InterruptedException {
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            circuit.recordFailure();
        }
        Thread.sleep(OPEN_DURATION.toMillis() + 100);

        assertThat(circuit.tryAcquire()).isTrue();

        Thread.sleep(OPEN_DURATION.toMillis() + 100);

        assertThat(circuit.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("恢复标记只消费一次")
    void consumeRecoveryRequired_consumesOnlyOnce() {
        assertThat(circuit.consumeRecoveryRequired()).isFalse();
    }

    @Test
    @DisplayName("isDegraded() 在关闭态返回 false，在打开态和半开态返回 true")
    void isDegraded_reflectsState() throws InterruptedException {
        assertThat(circuit.isDegraded()).isFalse();

        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            circuit.recordFailure();
        }
        assertThat(circuit.isDegraded()).isTrue();

        Thread.sleep(OPEN_DURATION.toMillis() + 100);
        circuit.tryAcquire();
        assertThat(circuit.isDegraded()).isTrue();

        circuit.recordSuccess();
        assertThat(circuit.isDegraded()).isFalse();
    }
}
