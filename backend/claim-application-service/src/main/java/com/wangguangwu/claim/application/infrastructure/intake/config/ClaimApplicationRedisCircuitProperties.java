package com.wangguangwu.claim.application.infrastructure.intake.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 可用性熔断的配置适配器。
 *
 * @param failureThreshold 连续失败多少次后打开熔断
 * @param openDuration     熔断打开时长，到期后放行一次探测
 * @param logInterval      降级日志的最小输出间隔
 * @author wangguangwu
 * @since 2026-10-30
 */
@ConfigurationProperties(prefix = "claim.redis.circuit")
public record ClaimApplicationRedisCircuitProperties(
        int failureThreshold,
        Duration openDuration,
        Duration logInterval) {
}
