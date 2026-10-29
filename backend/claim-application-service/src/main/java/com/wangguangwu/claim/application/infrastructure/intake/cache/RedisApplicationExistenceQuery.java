package com.wangguangwu.claim.application.infrastructure.intake.cache;

import com.wangguangwu.claim.application.domain.claim.port.ApplicationExistenceQuery;
import com.wangguangwu.claim.application.domain.claim.port.ApplicationRepository;
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
public class RedisApplicationExistenceQuery implements ApplicationExistenceQuery {

    private static final String CACHE_KEY_PREFIX = "claim:application:exists:";
    private static final Duration EXIST_TTL = Duration.ofMinutes(10);
    private static final Duration ABSENT_TTL = Duration.ofMinutes(1);

    private final RedissonClient redissonClient;
    private final ApplicationRepository applicationRepository;

    /**
     * 先读缓存，未命中时回源 MySQL 并以不同 TTL 缓存存在与不存在结果。
     *
     * @param tenantCode            租户编码
     * @param externalApplicationNo 外部申请编号
     * @return 存在时为 true
     */
    @Override
    public boolean exists(String tenantCode, String externalApplicationNo) {
        String cacheKey = cacheKey(tenantCode, externalApplicationNo);
        try {
            RBucket<Boolean> bucket = redissonClient.getBucket(cacheKey);
            Boolean cached = bucket.get();
            if (cached != null) {
                return cached;
            }

            boolean applicationExists = applicationRepository.exists(tenantCode, externalApplicationNo);
            bucket.set(applicationExists, applicationExists ? EXIST_TTL : ABSENT_TTL);
            return applicationExists;
        } catch (RedisException exception) {
            log.warn("理赔申请存在性缓存不可用，回源 MySQL，cacheKey={}", cacheKey, exception);
            return applicationRepository.exists(tenantCode, externalApplicationNo);
        }
    }

    /**
     * 在创建或重新申请成功后删除缓存，避免保留旧的存在性判断结果。
     *
     * @param tenantCode            租户编码
     * @param externalApplicationNo 外部申请编号
     */
    @Override
    public void evict(String tenantCode, String externalApplicationNo) {
        String cacheKey = cacheKey(tenantCode, externalApplicationNo);
        try {
            redissonClient.getBucket(cacheKey).delete();
        } catch (RedisException exception) {
            log.warn("理赔申请存在性缓存失效失败，cacheKey={}", cacheKey, exception);
        }
    }

    private String cacheKey(String tenantCode, String externalApplicationNo) {
        return CACHE_KEY_PREFIX + Sha256Digest.hex(tenantCode + ':' + externalApplicationNo);
    }
}
