package com.wangguangwu.claim.application.infrastructure.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;

/**
 * Redisson 签收锁获取、降级与释放测试。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class RedissonApplicationIntakeLockTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);

    private RedissonApplicationIntakeLock applicationIntakeLock;

    /**
     * 初始化待测锁并固定返回同一个 RLock 实例。
     */
    @BeforeEach
    void setUp() {
        applicationIntakeLock = new RedissonApplicationIntakeLock(redissonClient);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
    }

    /**
     * 获取锁成功后应执行受保护操作并由当前线程释放。
     */
    @Test
    void shouldExecuteActionAndUnlockWhenLockAcquired() throws InterruptedException {
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = applicationIntakeLock.execute("tenant-a", "external-1", () -> "done");

        assertThat(result).isEqualTo("done");
        verify(lock).unlock();
    }

    /**
     * 等待锁超时属于可重试的并发冲突，不得执行受保护操作。
     */
    @Test
    void shouldRejectIntakeWhenLockWaitTimesOut() throws InterruptedException {
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);

        assertThatThrownBy(() -> applicationIntakeLock.execute("tenant-a", "external-1", () -> "done"))
                .isInstanceOf(ApplicationIntakeException.class)
                .satisfies(exception -> assertThat(((ApplicationIntakeException) exception).getErrorCode())
                        .isEqualTo(ApplicationIntakeErrorCode.APPLICATION_INTAKE_BUSY));
        verify(lock, never()).unlock();
    }

    /**
     * Redis 不可用时必须降级为无锁执行，由数据库唯一约束兜底。
     */
    @Test
    void shouldExecuteActionWithoutLockWhenRedisUnavailable() throws InterruptedException {
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new RedisException("redis down"));

        String result = applicationIntakeLock.execute("tenant-a", "external-1", () -> "degraded");

        assertThat(result).isEqualTo("degraded");
        verify(lock, never()).unlock();
    }

    /**
     * 等待锁被中断时必须恢复中断标记并返回并发冲突。
     */
    @Test
    void shouldRestoreInterruptFlagWhenLockWaitInterrupted() throws InterruptedException {
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());

        assertThatThrownBy(() -> applicationIntakeLock.execute("tenant-a", "external-1", () -> "done"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("申请签收等待锁被中断，请稍后重试");
        assertThat(Thread.interrupted()).isTrue();
    }
}
