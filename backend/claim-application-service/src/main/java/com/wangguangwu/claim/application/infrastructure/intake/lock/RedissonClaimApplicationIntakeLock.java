package com.wangguangwu.claim.application.infrastructure.intake.lock;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeException;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationIntakeLock;
import com.wangguangwu.claim.application.infrastructure.intake.config.ClaimApplicationIntakeProperties;
import com.wangguangwu.claim.application.infrastructure.intake.resilience.RedisAvailabilityCircuit;
import com.wangguangwu.claim.application.shared.infrastructure.Sha256Digest;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Component;

/**
 * 基于 Redisson watchdog 的同外部申请号签收锁。
 *
 * <p>Redis 不可用时降级为无锁执行，由数据库唯一约束和重新申请状态条件保证正确性；
 * 降级路径先获取本节点信号量，超出降级并发上限直接返回系统异常，不排队。
 * 正常路径不获取信号量。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@Component
public class RedissonClaimApplicationIntakeLock implements ClaimApplicationIntakeLock {

    private static final String LOCK_KEY_PREFIX = "claim-applicationintake:lock:";
    private static final long WAIT_SECONDS = 30L;
    private static final String DEGRADED_CONCURRENCY_MESSAGE = "Redis 不可用且本节点签收并发已达上限，请稍后重试";

    private final RedissonClient redissonClient;
    private final RedisAvailabilityCircuit redisAvailabilityCircuit;
    private final Semaphore degradedPermits;

    /**
     * 按配置的降级并发上限初始化信号量。
     *
     * @param redissonClient           Redisson 客户端
     * @param redisAvailabilityCircuit Redis 可用性熔断
     * @param properties               签收配置
     */
    public RedissonClaimApplicationIntakeLock(RedissonClient redissonClient,
                                              RedisAvailabilityCircuit redisAvailabilityCircuit,
                                              ClaimApplicationIntakeProperties properties) {
        this.redissonClient = redissonClient;
        this.redisAvailabilityCircuit = redisAvailabilityCircuit;
        this.degradedPermits = new Semaphore(properties.degradedMaxConcurrency());
    }

    /**
     * 最多等待 30 秒获取锁，不指定 leaseTime，由 watchdog 每 30 秒续期。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @param action                受锁保护的操作
     * @param <T>                   返回类型
     * @return 操作结果
     */
    @Override
    public <T> T execute(String tenantCode, String externalClaimApplicationNo, Supplier<T> action) {
        if (!redisAvailabilityCircuit.tryAcquire()) {
            return executeDegraded(action);
        }
        RLock lock = redissonClient.getLock(
                LOCK_KEY_PREFIX + Sha256Digest.hex(tenantCode + ':' + externalClaimApplicationNo));
        boolean locked;
        try {
            locked = lock.tryLock(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClaimApplicationIntakeException(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_BUSY,
                    "申请签收等待锁被中断，请稍后重试");
        } catch (RedisException exception) {
            redisAvailabilityCircuit.recordFailure();
            return executeDegraded(action);
        }
        redisAvailabilityCircuit.recordSuccess();
        if (!locked) {
            throw new ClaimApplicationIntakeException(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_BUSY,
                    "同一申请的签收请求正在处理中，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            unlock(lock);
        }
    }

    /**
     * 降级执行：先进入降级日志统计，再按信号量控制并发，拿不到名额直接系统异常。
     *
     * @param action 原本受锁保护的操作
     * @param <T>    返回类型
     * @return 操作结果
     */
    private <T> T executeDegraded(Supplier<T> action) {
        redisAvailabilityCircuit.enterDegraded("签收锁");
        try {
            if (!degradedPermits.tryAcquire()) {
                throw new ClaimApplicationIntakeException(
                        ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR,
                        DEGRADED_CONCURRENCY_MESSAGE);
            }
            try {
                return action.get();
            } finally {
                degradedPermits.release();
            }
        } finally {
            redisAvailabilityCircuit.exitDegraded();
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
