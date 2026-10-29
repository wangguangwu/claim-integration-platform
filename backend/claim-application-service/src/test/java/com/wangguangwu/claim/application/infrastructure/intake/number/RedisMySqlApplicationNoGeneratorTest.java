package com.wangguangwu.claim.application.infrastructure.intake.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ApplicationRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;

/**
 * Redis 日计数与 MySQL 兜底的申请编号生成测试。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class RedisMySqlApplicationNoGeneratorTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final RAtomicLong counter = mock(RAtomicLong.class);
    private final RLock initializeLock = mock(RLock.class);

    private String formattedDate;
    private RedisMySqlApplicationNoGenerator applicationNoGenerator;

    /**
     * 初始化待测生成器并固定 Redis 计数与初始化锁。
     */
    @BeforeEach
    void setUp() {
        formattedDate = LocalDate.now(BUSINESS_ZONE_ID).format(DATE_FORMATTER);
        applicationNoGenerator = new RedisMySqlApplicationNoGenerator(redissonClient, applicationRepository);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(counter);
        when(redissonClient.getLock(anyString())).thenReturn(initializeLock);
    }

    /**
     * 计数器已存在时直接递增，不查询 MySQL。
     */
    @Test
    void shouldUseRedisCounterWhenPresent() throws InterruptedException {
        when(counter.isExists()).thenReturn(true);
        when(counter.incrementAndGet()).thenReturn(12L);

        String applicationNo = applicationNoGenerator.nextApplicationNo();

        assertThat(applicationNo).isEqualTo("A" + formattedDate + "00000012");
        verify(applicationRepository, never()).findMaxApplicationNoByPrefix(anyString());
    }

    /**
     * 计数器缺失时应在初始化锁内以 MySQL 当天最大尾号恢复计数。
     */
    @Test
    void shouldInitializeCounterFromMySqlWhenAbsent() throws InterruptedException {
        when(counter.isExists()).thenReturn(false);
        when(initializeLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(initializeLock.isHeldByCurrentThread()).thenReturn(true);
        when(applicationRepository.findMaxApplicationNoByPrefix(anyString()))
                .thenReturn(Optional.of("A" + formattedDate + "00000007"));
        when(counter.incrementAndGet()).thenReturn(8L);

        String applicationNo = applicationNoGenerator.nextApplicationNo();

        assertThat(applicationNo).isEqualTo("A" + formattedDate + "00000008");
        verify(counter).set(7L);
        verify(initializeLock).unlock();
    }

    /**
     * Redis 不可用时必须直接按 MySQL 最大尾号加一生成候选编号。
     */
    @Test
    void shouldFallBackToMySqlWhenRedisUnavailable() throws InterruptedException {
        when(counter.isExists()).thenThrow(new RedisException("redis down"));
        when(applicationRepository.findMaxApplicationNoByPrefix(anyString()))
                .thenReturn(Optional.of("A" + formattedDate + "00000003"));

        String applicationNo = applicationNoGenerator.nextApplicationNo();

        assertThat(applicationNo).isEqualTo("A" + formattedDate + "00000004");
        verify(counter, never()).incrementAndGet();
    }

    /**
     * 初始化锁等待超时时不得递增未初始化的计数器，改用 MySQL 候选编号。
     */
    @Test
    void shouldFallBackToMySqlWhenInitializeLockTimesOut() throws InterruptedException {
        when(counter.isExists()).thenReturn(false);
        when(initializeLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(applicationRepository.findMaxApplicationNoByPrefix(anyString()))
                .thenReturn(Optional.of("A" + formattedDate + "00000005"));

        String applicationNo = applicationNoGenerator.nextApplicationNo();

        assertThat(applicationNo).isEqualTo("A" + formattedDate + "00000006");
        verify(counter, never()).set(anyLong());
        verify(counter, never()).incrementAndGet();
    }

    /**
     * 等待初始化锁被中断时必须恢复中断标记并改用 MySQL 候选编号。
     */
    @Test
    void shouldRestoreInterruptFlagWhenInitializeLockInterrupted() throws InterruptedException {
        when(counter.isExists()).thenReturn(false);
        when(initializeLock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        when(applicationRepository.findMaxApplicationNoByPrefix(anyString()))
                .thenReturn(Optional.of("A" + formattedDate + "00000009"));

        String applicationNo = applicationNoGenerator.nextApplicationNo();

        assertThat(applicationNo).isEqualTo("A" + formattedDate + "00000010");
        assertThat(Thread.interrupted()).isTrue();
    }

    /**
     * 计数器落后于数据库时必须抬升到 MySQL 当天最大尾号。
     */
    @Test
    void shouldRaiseCounterToMySqlMaxSequenceWhenSynchronizing() {
        when(applicationRepository.findMaxApplicationNoByPrefix(anyString()))
                .thenReturn(Optional.of("A" + formattedDate + "00000005"));
        when(counter.get()).thenReturn(1L);
        when(counter.compareAndSet(1L, 5L)).thenReturn(true);

        applicationNoGenerator.synchronizeDailyCounter();

        verify(counter).compareAndSet(1L, 5L);
    }

    /**
     * 计数器领先数据库时必须保持不变，避免回退后重复发号。
     */
    @Test
    void shouldKeepCounterWhenItIsAheadOfMySql() {
        when(applicationRepository.findMaxApplicationNoByPrefix(anyString()))
                .thenReturn(Optional.of("A" + formattedDate + "00000005"));
        when(counter.get()).thenReturn(9L);

        applicationNoGenerator.synchronizeDailyCounter();

        verify(counter, never()).compareAndSet(anyLong(), anyLong());
    }

    /**
     * Redis 不可用时计数器校准必须静默跳过，由 MySQL 兜底保证发号正确。
     */
    @Test
    void shouldSkipSynchronizationWhenRedisUnavailable() {
        when(applicationRepository.findMaxApplicationNoByPrefix(anyString()))
                .thenReturn(Optional.of("A" + formattedDate + "00000005"));
        when(counter.get()).thenThrow(new RedisException("redis down"));

        applicationNoGenerator.synchronizeDailyCounter();

        verify(applicationRepository).findMaxApplicationNoByPrefix(anyString());
    }
}
