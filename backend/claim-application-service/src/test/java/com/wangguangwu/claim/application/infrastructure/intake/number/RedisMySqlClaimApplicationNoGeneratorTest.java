package com.wangguangwu.claim.application.infrastructure.intake.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.infrastructure.intake.config.ClaimApplicationRedisCircuitProperties;
import com.wangguangwu.claim.application.infrastructure.intake.resilience.RedisAvailabilityCircuit;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

/**
 * 发号器单元测试：验证降级与恢复行为。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("发号器")
class RedisMySqlClaimApplicationNoGeneratorTest {

    private static final String DATE_PREFIX = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.BASIC_ISO_DATE);
    private static final String EXPECTED_PREFIX = "A" + DATE_PREFIX;

    private RedissonClient redissonClient;
    private ClaimApplicationRepository claimApplicationRepository;
    private RedisAvailabilityCircuit circuit;

    private RedisMySqlClaimApplicationNoGenerator generator;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        ClaimApplicationRedisCircuitProperties properties = new ClaimApplicationRedisCircuitProperties(
                3, Duration.ofSeconds(10), Duration.ofSeconds(10));
        circuit = new RedisAvailabilityCircuit(properties);

        generator = new RedisMySqlClaimApplicationNoGenerator(redissonClient, claimApplicationRepository, circuit);
    }

    @Test
    @DisplayName("正常发号：Redis INCR 返回编号 → 格式化为 A + yyyyMMdd + 8 位序列")
    void nextClaimApplicationNo_normalPath_returnsFormattedNumber() {
        RAtomicLong counter = mock(RAtomicLong.class);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(counter);
        when(counter.isExists()).thenReturn(true);
        when(counter.incrementAndGet()).thenReturn(1L);

        String result = generator.nextClaimApplicationNo();

        assertThat(result).isEqualTo(EXPECTED_PREFIX + "00000001");
    }

    @Test
    @DisplayName("降级发号：熔断打开 → 本节点串行分配 → 返回递增编号")
    void nextClaimApplicationNo_degradedPath_returnsSequentialNumbers() {
        for (int i = 0; i < 3; i++) {
            circuit.recordFailure();
        }
        when(claimApplicationRepository.findMaxClaimApplicationNoByPrefix(EXPECTED_PREFIX)).thenReturn(Optional.empty());

        String first = generator.nextClaimApplicationNo();
        String second = generator.nextClaimApplicationNo();
        String third = generator.nextClaimApplicationNo();

        assertThat(first).isEqualTo(EXPECTED_PREFIX + "00000001");
        assertThat(second).isEqualTo(EXPECTED_PREFIX + "00000002");
        assertThat(third).isEqualTo(EXPECTED_PREFIX + "00000003");
    }

    @Test
    @DisplayName("恢复后抬升：熔断关闭 → 首次发号抬升计数器到 MySQL 最大尾号 → 继续 INCR")
    void nextClaimApplicationNo_afterRecovery_raisesCounterToMaxPersisted() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            circuit.recordFailure();
        }
        Thread.sleep(Duration.ofSeconds(10).toMillis() + 100);

        RAtomicLong counter = mock(RAtomicLong.class);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(counter);
        when(counter.isExists()).thenReturn(true);
        when(counter.get()).thenReturn(5L);
        when(counter.compareAndSet(anyLong(), anyLong())).thenReturn(true);
        when(counter.incrementAndGet()).thenReturn(11L);
        when(claimApplicationRepository.findMaxClaimApplicationNoByPrefix(EXPECTED_PREFIX))
                .thenReturn(Optional.of(EXPECTED_PREFIX + "00000010"));

        circuit.tryAcquire();
        circuit.recordSuccess();

        String result = generator.nextClaimApplicationNo();

        assertThat(result).isEqualTo(EXPECTED_PREFIX + "00000011");
    }

    @Test
    @DisplayName("降级路径记住已分配尾号：连续分配保证递增")
    void nextClaimApplicationNo_degradedPath_remembersLastAllocated() {
        for (int i = 0; i < 3; i++) {
            circuit.recordFailure();
        }
        when(claimApplicationRepository.findMaxClaimApplicationNoByPrefix(EXPECTED_PREFIX))
                .thenReturn(Optional.of(EXPECTED_PREFIX + "00000005"));

        String first = generator.nextClaimApplicationNo();
        String second = generator.nextClaimApplicationNo();

        assertThat(first).isEqualTo(EXPECTED_PREFIX + "00000006");
        assertThat(second).isEqualTo(EXPECTED_PREFIX + "00000007");
    }

    @Test
    @DisplayName("序号超限：当日序列超过 99,999,999 → 抛 IllegalStateException")
    void nextClaimApplicationNo_sequenceExceedsLimit_throwsIllegalStateException() {
        for (int i = 0; i < 3; i++) {
            circuit.recordFailure();
        }
        when(claimApplicationRepository.findMaxClaimApplicationNoByPrefix(EXPECTED_PREFIX))
                .thenReturn(Optional.of(EXPECTED_PREFIX + "99999999"));

        assertThatThrownBy(() -> generator.nextClaimApplicationNo())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("八位上限");
    }
}
