package com.wangguangwu.claim.application.infrastructure.intake.lock;

import com.wangguangwu.claim.application.domain.claim.ApplicationIntakeLock;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.shared.infrastructure.Sha256Digest;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Component;

/**
 * 基于 Redisson watchdog 的同外部申请号签收锁。
 *
 * <p>Redis 不可用时降级为无锁执行，由数据库唯一约束和重新申请状态条件保证正确性。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RedissonApplicationIntakeLock implements ApplicationIntakeLock {

    private static final String LOCK_KEY_PREFIX = "claim:application:intake:";
    private static final long WAIT_SECONDS = 30L;

    private final RedissonClient redissonClient;

    /**
     * 最多等待 30 秒获取锁，不指定 leaseTime，由 watchdog 每 30 秒续期。
     *
     * @param tenantCode            租户编码
     * @param externalApplicationNo 外部申请编号
     * @param action                受锁保护的操作
     * @param <T>                   返回类型
     * @return 操作结果
     */
    @Override
    public <T> T execute(String tenantCode, String externalApplicationNo, Supplier<T> action) {
        RLock lock = redissonClient.getLock(
                LOCK_KEY_PREFIX + Sha256Digest.hex(tenantCode + ':' + externalApplicationNo));
        if (!acquireOrDegrade(lock)) {
            return action.get();
        }
        try {
            return action.get();
        } finally {
            unlock(lock);
        }
    }

    /**
     * 尝试获取锁；获取失败超时抛出业务异常，Redis 不可用时降级为无锁。
     *
     * @param lock 分布式锁
     * @return 成功获取锁时为 true；Redis 不可用需要无锁执行时为 false
     */
    private boolean acquireOrDegrade(RLock lock) {
        try {
            if (lock.tryLock(WAIT_SECONDS, TimeUnit.SECONDS)) {
                return true;
            }
            throw new ApplicationIntakeException(ApplicationIntakeErrorCode.APPLICATION_INTAKE_BUSY,
                    "同一申请的签收请求正在处理中，请稍后重试");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApplicationIntakeException(ApplicationIntakeErrorCode.APPLICATION_INTAKE_BUSY,
                    "申请签收等待锁被中断，请稍后重试");
        } catch (RedisException exception) {
            log.warn("申请签收锁不可用，降级为无锁执行并依赖数据库唯一约束", exception);
            return false;
        }
    }

    /**
     * 仅在当前线程持锁时释放，避免 Redis 故障覆盖业务结果。
     *
     * @param lock 分布式锁
     */
    private void unlock(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RedisException exception) {
            log.warn("申请签收锁释放失败，等待 watchdog 超时自动释放", exception);
        }
    }
}

