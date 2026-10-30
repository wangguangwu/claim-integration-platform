package com.wangguangwu.claim.application.infrastructure.intake.config;

import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakePolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 签收策略与判定快照的配置适配器，TTL、并发上限与重试次数全部来自配置。
 *
 * @param snapshotCache        判定快照缓存参数
 * @param degradedMaxConcurrency Redis 降级期间单节点签收并发上限
 * @param uniqueKeyRetryTimes  唯一键冲突后的最大重试次数
 * @author wangguangwu
 * @since 2026-10-30
 */
@ConfigurationProperties(prefix = "claim.intake")
public record ClaimApplicationIntakeProperties(
        SnapshotCache snapshotCache,
        int degradedMaxConcurrency,
        int uniqueKeyRetryTimes) implements ClaimApplicationIntakePolicy {

    /**
     * 判定快照缓存参数，区分可复用、状态可变与不存在三种结论的存活时间。
     *
     * @param reusableTtl    可复用快照的存活时间
     * @param mutableTtl     存在但状态可变的标记存活时间
     * @param absentTtl      不存在标记的存活时间
     * @param localTtl       进程内一级缓存的存活时间
     * @param localMaxEntries 进程内一级缓存的条目上限
     */
    public record SnapshotCache(
            Duration reusableTtl,
            Duration mutableTtl,
            Duration absentTtl,
            Duration localTtl,
            int localMaxEntries) {
    }
}
