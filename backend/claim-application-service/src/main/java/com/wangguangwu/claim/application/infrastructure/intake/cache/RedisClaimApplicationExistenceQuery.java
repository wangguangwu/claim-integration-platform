package com.wangguangwu.claim.application.infrastructure.intake.cache;

import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationExistenceQuery;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.shared.infrastructure.Sha256Digest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Component;

/**
 * 使用 Redis 旁路缓存查询理赔申请是否存在。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RedisClaimApplicationExistenceQuery implements ClaimApplicationExistenceQuery {

    private static final String CACHE_KEY_PREFIX = "claim-applicationexists:";
    private static final Duration EXIST_TTL = Duration.ofMinutes(10);
    private static final Duration ABSENT_TTL = Duration.ofMinutes(1);

    private final RedissonClient redissonClient;
    private final ClaimApplicationRepository claimApplicationRepository;

    /**
     * 先读缓存，未命中时回源 MySQL 并以不同 TTL 缓存存在与不存在结果。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @return 存在时为 true
     */
    @Override
    public boolean exists(String tenantCode, String externalClaimApplicationNo) {
        String cacheKey = cacheKey(tenantCode, externalClaimApplicationNo);
        try {
            RBucket<Boolean> bucket = redissonClient.getBucket(cacheKey);
            Boolean cached = bucket.get();
            if (cached != null) {
                return cached;
            }

            boolean claimApplicationExists = claimApplicationRepository.exists(tenantCode, externalClaimApplicationNo);
            bucket.set(claimApplicationExists, claimApplicationExists ? EXIST_TTL : ABSENT_TTL);
            return claimApplicationExists;
        } catch (RedisException exception) {
            log.warn("理赔申请存在性缓存不可用，回源 MySQL，cacheKey={}", cacheKey, exception);
            return claimApplicationRepository.exists(tenantCode, externalClaimApplicationNo);
        }
    }

    /**
     * 在创建或重新申请成功后删除缓存，避免保留旧的存在性判断结果。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     */
    @Override
    public void evict(String tenantCode, String externalClaimApplicationNo) {
        String cacheKey = cacheKey(tenantCode, externalClaimApplicationNo);
        try {
            redissonClient.getBucket(cacheKey).delete();
        } catch (RedisException exception) {
            log.warn("理赔申请存在性缓存失效失败，cacheKey={}", cacheKey, exception);
        }
    }

    private String cacheKey(String tenantCode, String externalClaimApplicationNo) {
        return CACHE_KEY_PREFIX + Sha256Digest.hex(tenantCode + ':' + externalClaimApplicationNo);
    }
}
