package com.wangguangwu.claim.application.infrastructure.intake.cache;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeLookup;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeSnapshot;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationIntakeSnapshotQuery;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.infrastructure.intake.config.ClaimApplicationIntakeProperties;
import com.wangguangwu.claim.application.infrastructure.intake.resilience.RedisAvailabilityCircuit;
import com.wangguangwu.claim.application.shared.infrastructure.Sha256Digest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

/**
 * 使用 Redis 旁路缓存与进程内一级缓存查询签收判定快照。
 *
 * <p>只缓存三种固定结论：可复用（平台申请编号 + 安全状态）、存在但状态可变、不存在。
 * 安全状态在状态机中单调不可逆，因此本类没有任何失效动作；Redis 不可用时回源 MySQL，
 * 结论语义与命中缓存时一致。熔断打开期间不再发起 Redis 读写，只走进程内缓存与 MySQL。</p>
 *
 * @author wangguangwu
 * @since 2026-10-30
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RedisClaimApplicationIntakeSnapshotQuery implements ClaimApplicationIntakeSnapshotQuery {

    /** 与签收锁同用外部申请号摘要，前缀必须区分，否则字符串快照会覆盖 Redisson 锁结构。 */
    private static final String CACHE_KEY_PREFIX = "claim-applicationintake:snapshot:";
    private static final String ABSENT_MARKER = "N";
    private static final String MUTABLE_MARKER = "X";
    private static final char VALUE_SEPARATOR = '|';

    private final RedissonClient redissonClient;
    private final ClaimApplicationRepository claimApplicationRepository;
    private final ClaimApplicationIntakeProperties properties;
    private final RedisAvailabilityCircuit redisAvailabilityCircuit;

    private final Map<String, LocalLookupEntry> localLookups = new ConcurrentHashMap<>();

    /**
     * 依次读进程内缓存、Redis 与 MySQL，命中或回源后按结论写入对应 TTL 的标记。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @return 签收判定结论
     */
    @Override
    public ClaimApplicationIntakeLookup lookup(String tenantCode, String externalClaimApplicationNo) {
        String cacheKey = cacheKey(tenantCode, externalClaimApplicationNo);
        ClaimApplicationIntakeLookup localLookup = localLookup(cacheKey);
        if (localLookup != null) {
            log.debug("签收判定命中进程内快照，不回源，cacheKey={}，kind={}", cacheKey, localLookup.kind());
            return localLookup;
        }
        if (redisAvailabilityCircuit.tryAcquire()) {
            try {
                ClaimApplicationIntakeLookup cachedLookup = cachedLookup(cacheKey);
                redisAvailabilityCircuit.recordSuccess();
                if (cachedLookup != null) {
                    cacheLocally(cacheKey, cachedLookup);
                    log.debug("签收判定命中 Redis 快照，不回源，cacheKey={}，kind={}", cacheKey, cachedLookup.kind());
                    return cachedLookup;
                }
            } catch (RedisException exception) {
                redisAvailabilityCircuit.recordFailure();
                log.warn("签收判定快照不可用，回源 MySQL，cacheKey={}", cacheKey, exception);
            }
        }
        ClaimApplicationIntakeLookup lookup = lookupFromDatabase(tenantCode, externalClaimApplicationNo, cacheKey);
        cacheLocally(cacheKey, lookup);
        return lookup;
    }

    /**
     * 创建成功后把空标记覆盖为存在标记，避免刚创建的申请被误判为不存在。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     */
    @Override
    public void markMutable(String tenantCode, String externalClaimApplicationNo) {
        String cacheKey = cacheKey(tenantCode, externalClaimApplicationNo);
        cacheLocally(cacheKey, ClaimApplicationIntakeLookup.existing());
        writeQuietly(cacheKey, MUTABLE_MARKER, properties.snapshotCache().mutableTtl());
    }

    /**
     * 回源读到安全状态后把存在标记升级为可复用快照，后续重复请求不再读库。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @param snapshot              回源观察到的可复用快照
     */
    @Override
    public void markReusable(String tenantCode, String externalClaimApplicationNo,
                             ClaimApplicationIntakeSnapshot snapshot) {
        String cacheKey = cacheKey(tenantCode, externalClaimApplicationNo);
        cacheLocally(cacheKey, ClaimApplicationIntakeLookup.reusable(snapshot));
        writeQuietly(cacheKey, cachedValue(snapshot), properties.snapshotCache().reusableTtl());
    }

    /**
     * 回源读取聚合，按状态决定写入可复用快照、存在标记或空标记。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @param cacheKey              缓存键
     * @return 签收判定结论
     */
    private ClaimApplicationIntakeLookup lookupFromDatabase(String tenantCode, String externalClaimApplicationNo,
                                                           String cacheKey) {
        Optional<ClaimApplication> claimApplication = claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(
                tenantCode, externalClaimApplicationNo);
        if (claimApplication.isEmpty()) {
            writeQuietly(cacheKey, ABSENT_MARKER, properties.snapshotCache().absentTtl());
            return ClaimApplicationIntakeLookup.absent();
        }
        ClaimApplicationStatus status = claimApplication.get().getStatus();
        if (!ClaimApplicationIntakeSnapshot.isReusableStatus(status)) {
            writeQuietly(cacheKey, MUTABLE_MARKER, properties.snapshotCache().mutableTtl());
            return ClaimApplicationIntakeLookup.existing();
        }
        ClaimApplicationIntakeSnapshot snapshot = new ClaimApplicationIntakeSnapshot(
                claimApplication.get().getClaimApplicationNo(), status);
        writeQuietly(cacheKey, cachedValue(snapshot), properties.snapshotCache().reusableTtl());
        return ClaimApplicationIntakeLookup.reusable(snapshot);
    }

    /**
     * 读取 Redis 中的快照值；内容无法识别时按未命中处理并回源。
     *
     * @param cacheKey 缓存键
     * @return 命中的判定结论；未命中时为 null
     */
    private ClaimApplicationIntakeLookup cachedLookup(String cacheKey) {
        RBucket<String> bucket = redissonClient.getBucket(cacheKey, StringCodec.INSTANCE);
        return toLookup(bucket.get());
    }

    /**
     * 把缓存值还原为判定结论。
     *
     * @param cachedValue 缓存值
     * @return 判定结论；空值或无法识别时为 null
     */
    private ClaimApplicationIntakeLookup toLookup(String cachedValue) {
        if (cachedValue == null) {
            return null;
        }
        if (ABSENT_MARKER.equals(cachedValue)) {
            return ClaimApplicationIntakeLookup.absent();
        }
        if (MUTABLE_MARKER.equals(cachedValue)) {
            return ClaimApplicationIntakeLookup.existing();
        }
        int separatorIndex = cachedValue.indexOf(VALUE_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == cachedValue.length() - 1) {
            log.warn("签收判定快照内容无法识别，按未命中回源，cachedValue={}", cachedValue);
            return null;
        }
        try {
            ClaimApplicationStatus status = ClaimApplicationStatus.valueOf(
                    cachedValue.substring(separatorIndex + 1));
            return ClaimApplicationIntakeLookup.reusable(new ClaimApplicationIntakeSnapshot(
                    cachedValue.substring(0, separatorIndex), status));
        } catch (IllegalArgumentException exception) {
            log.warn("签收判定快照内容无法识别，按未命中回源，cachedValue={}", cachedValue);
            return null;
        }
    }

    /**
     * 读取进程内一级缓存，超过存活时间即删除并视为未命中。
     *
     * @param cacheKey 缓存键
     * @return 判定结论；未命中或已过期时为 null
     */
    private ClaimApplicationIntakeLookup localLookup(String cacheKey) {
        LocalLookupEntry localEntry = localLookups.get(cacheKey);
        if (localEntry == null) {
            return null;
        }
        if (System.nanoTime() - localEntry.writtenAtNanos() >= properties.snapshotCache().localTtl().toNanos()) {
            localLookups.remove(cacheKey, localEntry);
            return null;
        }
        return localEntry.lookup();
    }

    /**
     * 写入进程内一级缓存，达到条目上限后不再写入，由存活时间自然淘汰。
     *
     * @param cacheKey 缓存键
     * @param lookup   判定结论
     */
    private void cacheLocally(String cacheKey, ClaimApplicationIntakeLookup lookup) {
        boolean newEntry = !localLookups.containsKey(cacheKey);
        if (newEntry && localLookups.size() >= properties.snapshotCache().localMaxEntries()) {
            return;
        }
        localLookups.put(cacheKey, new LocalLookupEntry(lookup, System.nanoTime()));
    }

    /**
     * 写入 Redis 标记；写入失败只记录日志，不改变本次签收结论。
     *
     * <p>用字符串编解码保证 Redis 中保存的就是可读标记，运维可直接 GET 校验。</p>
     *
     * @param cacheKey    缓存键
     * @param cachedValue 缓存值
     * @param ttl         存活时间
     */
    private void writeQuietly(String cacheKey, String cachedValue, Duration ttl) {
        if (redisAvailabilityCircuit.isDegraded()) {
            return;
        }
        try {
            redissonClient.<String>getBucket(cacheKey, StringCodec.INSTANCE).set(cachedValue, ttl);
        } catch (RedisException exception) {
            log.warn("签收判定快照写入失败，本次签收结论不受影响，cacheKey={}", cacheKey, exception);
        }
    }

    private String cachedValue(ClaimApplicationIntakeSnapshot snapshot) {
        return snapshot.claimApplicationNo() + VALUE_SEPARATOR + snapshot.status().name();
    }

    private String cacheKey(String tenantCode, String externalClaimApplicationNo) {
        return CACHE_KEY_PREFIX + Sha256Digest.hex(tenantCode + ':' + externalClaimApplicationNo);
    }

    /**
     * 进程内一级缓存条目，同时记录写入时刻以判断是否过期。
     *
     * @param lookup         判定结论
     * @param writtenAtNanos 写入时刻的纳秒时间戳
     */
    private record LocalLookupEntry(ClaimApplicationIntakeLookup lookup, long writtenAtNanos) {
    }
}
