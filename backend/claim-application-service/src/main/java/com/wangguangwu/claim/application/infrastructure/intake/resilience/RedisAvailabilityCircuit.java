package com.wangguangwu.claim.application.infrastructure.intake.resilience;

import com.wangguangwu.claim.application.infrastructure.intake.config.ClaimApplicationRedisCircuitProperties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Redis 可用性熔断，连续失败达到阈值后打开固定时长，到期只放行一次探测。
 *
 * <p>熔断打开期间，锁、判定快照与发号不再发起 Redis 调用，直接走各自的降级路径，
 * 避免每个请求都等待 Redis 超时。探测成功立即关闭并标记"需要抬升发号计数器"，
 * 由发号器消费一次，保证恢复后的第一个编号不早于数据库已落库的最大尾号。</p>
 *
 * @author wangguangwu
 * @since 2026-10-30
 */
@Slf4j
@Component
public class RedisAvailabilityCircuit {

    private final int failureThreshold;
    private final long openDurationNanos;
    private final long logIntervalNanos;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicBoolean probeInFlight = new AtomicBoolean();
    private final AtomicBoolean recoveryRequired = new AtomicBoolean();
    private final AtomicInteger degradedInFlight = new AtomicInteger();
    private final AtomicLong lastDegradedLogNanos = new AtomicLong();

    private volatile CircuitState state = CircuitState.CLOSED;
    private volatile long openUntilNanos;
    private volatile long probeStartedAtNanos;

    /**
     * 按配置初始化熔断阈值、打开时长与降级日志间隔。
     *
     * @param properties Redis 熔断配置
     */
    public RedisAvailabilityCircuit(ClaimApplicationRedisCircuitProperties properties) {
        this.failureThreshold = properties.failureThreshold();
        this.openDurationNanos = properties.openDuration().toNanos();
        this.logIntervalNanos = properties.logInterval().toNanos();
        this.lastDegradedLogNanos.set(System.nanoTime() - this.logIntervalNanos);
    }

    /**
     * 申请一次 Redis 调用许可：关闭态直接放行，打开态到期后只放行一个探测。
     *
     * @return 允许发起 Redis 调用时为 true；需要走降级路径时为 false
     */
    public boolean tryAcquire() {
        CircuitState currentState = state;
        if (currentState == CircuitState.CLOSED) {
            return true;
        }
        if (currentState == CircuitState.OPEN && System.nanoTime() - openUntilNanos < 0) {
            return false;
        }
        if (currentState == CircuitState.HALF_OPEN && probeExpired()) {
            probeInFlight.set(false);
        }
        return startProbe();
    }

    /**
     * 熔断打开或半开且探测占用中时为 true，只用于跳过非关键的缓存写入。
     *
     * @return 处于降级路径时为 true
     */
    public boolean isDegraded() {
        return state != CircuitState.CLOSED;
    }

    /**
     * 记录一次 Redis 调用成功；半开探测成功即关闭熔断并要求抬升发号计数器。
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        probeInFlight.set(false);
        if (state == CircuitState.CLOSED) {
            return;
        }
        state = CircuitState.CLOSED;
        recoveryRequired.set(true);
        log.info("Redis 探测成功，熔断关闭，恢复后的首次发号将先抬升计数器");
    }

    /**
     * 记录一次 Redis 调用失败；连续失败达到阈值或探测失败时打开熔断。
     */
    public void recordFailure() {
        probeInFlight.set(false);
        if (state == CircuitState.HALF_OPEN) {
            open();
            return;
        }
        if (state == CircuitState.CLOSED && consecutiveFailures.incrementAndGet() >= failureThreshold) {
            open();
        }
    }

    /**
     * 消费"恢复后需要抬升计数器"的标记，只返回 true 一次。
     *
     * @return 本次发号是否需要先抬升计数器
     */
    public boolean consumeRecoveryRequired() {
        return recoveryRequired.getAndSet(false);
    }

    /**
     * 进入降级路径，按最小间隔输出降级日志并携带当前降级并发数。
     *
     * @param reason 降级原因，例如锁、判定快照、发号
     */
    public void enterDegraded(String reason) {
        int concurrency = degradedInFlight.incrementAndGet();
        long now = System.nanoTime();
        long lastLogNanos = lastDegradedLogNanos.get();
        if (now - lastLogNanos >= logIntervalNanos && lastDegradedLogNanos.compareAndSet(lastLogNanos, now)) {
            log.warn("Redis 不可用，签收进入降级路径，reason={}，concurrency={}", reason, concurrency);
        }
    }

    /**
     * 退出降级路径。
     */
    public void exitDegraded() {
        degradedInFlight.decrementAndGet();
    }

    /**
     * 占用唯一探测名额并转入半开，占用失败说明已有探测在飞。
     *
     * @return 本次请求承担探测职责时为 true
     */
    private boolean startProbe() {
        if (!probeInFlight.compareAndSet(false, true)) {
            return false;
        }
        probeStartedAtNanos = System.nanoTime();
        state = CircuitState.HALF_OPEN;
        return true;
    }

    /**
     * 判断当前探测是否已超时。
     *
     * <p>探测请求可能被中断而无法上报结果，例如等待分布式锁时线程被 interrupt。
     * 超时后重新放行探测，避免熔断永久停在半开、只能靠重启恢复。</p>
     *
     * @return 探测占用超过一个熔断周期时为 true
     */
    private boolean probeExpired() {
        return System.nanoTime() - probeStartedAtNanos >= openDurationNanos;
    }

    /**
     * 打开熔断并重置失败计数，到期时间按配置的打开时长计算。
     */
    private void open() {
        state = CircuitState.OPEN;
        openUntilNanos = System.nanoTime() + openDurationNanos;
        consecutiveFailures.set(0);
        log.warn("Redis 连续失败达到阈值，熔断打开，failureThreshold={}，openDurationMillis={}",
                failureThreshold, TimeUnit.NANOSECONDS.toMillis(openDurationNanos));
    }

    /**
     * 熔断状态。
     */
    private enum CircuitState {

        /**
         * 正常调用 Redis。
         */
        CLOSED,

        /**
         * 跳 Redis 直连降级路径。
         */
        OPEN,

        /**
         * 已放行一次探测，等待探测结果。
         */
        HALF_OPEN
    }
}
