package com.wangguangwu.claim.application.infrastructure.intake.number;

import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationNoGenerator;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.infrastructure.intake.resilience.RedisAvailabilityCircuit;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 当前日计数、MySQL 最大申请编号兜底的理赔申请编号生成器。
 *
 * <p>Redis 熔断打开时改走本节点串行降级路径：在 {@code synchronized} 段内取「数据库当天最大尾号与本节点已分配尾号」
 * 的较大者加一，不写 Redis。分配与落库不在同一临界区，只读最大尾号会让并发请求拿到同一个号，
 * 记住本节点已分配尾号才能保证同节点连续分配递增；跨节点冲突仍由唯一键约束与用例重试收敛。
 * 熔断关闭后的首次发号先把计数器抬升到数据库最大尾号，再继续 {@code INCR}。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RedisMySqlClaimApplicationNoGenerator implements ClaimApplicationNoGenerator {

    private static final String NUMBER_KEY_PREFIX = "claim-applicationnumber:";
    private static final String INITIALIZE_LOCK_KEY_PREFIX = "claim-applicationnumber:initialize:";
    private static final String CLAIM_APPLICATION_NO_PREFIX = "A";
    private static final int SERIAL_NUMBER_LENGTH = 8;
    private static final long MAX_DAILY_SEQUENCE = 99_999_999L;
    private static final long INITIALIZE_LOCK_WAIT_SECONDS = 30L;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final RedissonClient redissonClient;
    private final ClaimApplicationRepository claimApplicationRepository;
    private final RedisAvailabilityCircuit redisAvailabilityCircuit;

    /** 降级发号的本节点已分配尾号，与 {@link #lastAllocatedSequence} 一起只在串行化方法内读写。 */
    private String lastAllocatedDate;
    private long lastAllocatedSequence;

    /**
     * 生成 A 加日期加八位序列的理赔申请编号。
     *
     * @return 全局唯一理赔申请编号
     */
    @Override
    public String nextClaimApplicationNo() {
        LocalDate sequenceDate = LocalDate.now(BUSINESS_ZONE_ID);
        String formattedDate = sequenceDate.format(DATE_FORMATTER);
        if (!redisAvailabilityCircuit.tryAcquire()) {
            return nextClaimApplicationNoDegraded(formattedDate);
        }
        try {
            String claimApplicationNo = nextClaimApplicationNoFromRedis(formattedDate);
            redisAvailabilityCircuit.recordSuccess();
            return claimApplicationNo;
        } catch (RedisException exception) {
            redisAvailabilityCircuit.recordFailure();
            return nextClaimApplicationNoDegraded(formattedDate);
        }
    }

    /**
     * 走 Redis 计数发号；熔断刚关闭时先抬升计数器，避免编号回退撞号。
     *
     * @param formattedDate 格式化后的序列日期
     * @return 理赔申请编号
     */
    private String nextClaimApplicationNoFromRedis(String formattedDate) {
        RAtomicLong counter = redissonClient.getAtomicLong(NUMBER_KEY_PREFIX + formattedDate);
        if (redisAvailabilityCircuit.consumeRecoveryRequired()) {
            raiseCounterTo(counter, currentDailySequence(formattedDate));
        }
        if (!initializeCounterIfAbsent(formattedDate, counter)) {
            return nextClaimApplicationNoDegraded(formattedDate);
        }
        return formatClaimApplicationNo(formattedDate, counter.incrementAndGet());
    }

    /**
     * 降级发号：本节点串行分配尾号，并在降级日志中记录当前降级并发。
     *
     * @param formattedDate 格式化后的序列日期
     * @return 理赔申请编号
     */
    private String nextClaimApplicationNoDegraded(String formattedDate) {
        redisAvailabilityCircuit.enterDegraded("发号");
        try {
            return nextClaimApplicationNoSerialized(formattedDate);
        } finally {
            redisAvailabilityCircuit.exitDegraded();
        }
    }

    /**
     * Redis 当前日计数不存在时，以 MySQL 当天最大申请编号恢复计数器。
     *
     * <p>初始化锁有等待上限；未在期限内取得锁说明 Redis 侧不可用或竞争激烈，
     * 直接放弃写计数器，避免生成从 1 开始而必然冲突的编号。</p>
     *
     * @param formattedDate 格式化后的序列日期
     * @param counter      Redis 计数器
     * @return 计数器可用时为 true
     */
    private boolean initializeCounterIfAbsent(String formattedDate, RAtomicLong counter) {
        if (counter.isExists()) {
            return true;
        }
        RLock lock = redissonClient.getLock(INITIALIZE_LOCK_KEY_PREFIX + formattedDate);
        boolean locked;
        try {
            locked = lock.tryLock(INITIALIZE_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (!locked) {
            return false;
        }
        try {
            if (!counter.isExists()) {
                counter.set(currentDailySequence(formattedDate));
            }
            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 将 Redis 当日计数器抬升到 MySQL 已落库的最大尾号。
     *
     * <p>只抬升不降低：计数器领先数据库时继续使用 Redis 计数，Redis 不可用时静默跳过，
     * 由后续发号的 MySQL 兜底保证正确性。</p>
     */
    @Override
    public void synchronizeDailyCounter() {
        String formattedDate = LocalDate.now(BUSINESS_ZONE_ID).format(DATE_FORMATTER);
        long persistedSequence = currentDailySequence(formattedDate);
        RAtomicLong counter = redissonClient.getAtomicLong(NUMBER_KEY_PREFIX + formattedDate);
        try {
            raiseCounterTo(counter, persistedSequence);
        } catch (RedisException exception) {
            log.warn("理赔申请编号计数器校准失败，继续使用 MySQL 兜底，date={}", formattedDate, exception);
        }
    }

    /**
     * 以 CAS 循环抬升计数器，避免覆盖并发请求已经递增的计数。
     *
     * @param counter            Redis 计数器
     * @param persistedSequence  MySQL 已落库的最大尾号
     */
    private void raiseCounterTo(RAtomicLong counter, long persistedSequence) {
        long currentSequence = counter.get();
        while (currentSequence < persistedSequence) {
            if (counter.compareAndSet(currentSequence, persistedSequence)) {
                return;
            }
            currentSequence = counter.get();
        }
    }

    /**
     * Redis 不可用时，同节点降级发号串行化。
     *
     * <p>分配与落库不在同一临界区，只读数据库最大尾号会让并发请求拿到同一个号；
     * 记住本节点已分配的尾号后，同一节点连续分配必然递增。</p>
     *
     * @param formattedDate 格式化后的序列日期
     * @return 待持久化的理赔申请编号
     */
    private synchronized String nextClaimApplicationNoSerialized(String formattedDate) {
        long persistedSequence = currentDailySequence(formattedDate);
        long sequence = formattedDate.equals(lastAllocatedDate)
                ? Math.max(persistedSequence, lastAllocatedSequence) + 1
                : persistedSequence + 1;
        lastAllocatedDate = formattedDate;
        lastAllocatedSequence = sequence;
        return formatClaimApplicationNo(formattedDate, sequence);
    }

    /**
     * 取得当天已持久化编号中的最大尾号。
     *
     * @param formattedDate 格式化后的序列日期
     * @return 当前最大尾号；当天无数据时为 0
     */
    private long currentDailySequence(String formattedDate) {
        String numberPrefix = CLAIM_APPLICATION_NO_PREFIX + formattedDate;
        return claimApplicationRepository.findMaxClaimApplicationNoByPrefix(numberPrefix)
                .map(maximumClaimApplicationNo -> Long.parseLong(maximumClaimApplicationNo.substring(numberPrefix.length())))
                .orElse(0L);
    }

    /**
     * 将日期和日序列拼接为固定格式编号，并校验日序列上限。
     *
     * @param formattedDate 格式化后的序列日期
     * @param sequence      当天序列
     * @return 理赔申请编号
     */
    private String formatClaimApplicationNo(String formattedDate, long sequence) {
        if (sequence < 1 || sequence > MAX_DAILY_SEQUENCE) {
            throw new IllegalStateException("理赔申请当日编号序列超出八位上限，日期=" + formattedDate);
        }
        return CLAIM_APPLICATION_NO_PREFIX + formattedDate + String.format("%0" + SERIAL_NUMBER_LENGTH + "d", sequence);
    }
}
