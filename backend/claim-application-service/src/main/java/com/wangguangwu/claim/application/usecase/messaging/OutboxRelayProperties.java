package com.wangguangwu.claim.application.usecase.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox 投递参数，批量、间隔与退避阈值全部来自配置。
 *
 * @param fixedDelayMs      两次投递调度的间隔
 * @param batchSize         单次投递处理的最大事件数
 * @param maxRetryCount     允许的最大投递次数，达到后置为失败
 * @param retryBaseSeconds  退避基准秒数，第 n 次失败等待 base × 2^(n-1)
 * @param retryMaxSeconds   单次退避等待上限
 * @author wangguangwu
 * @since 2026-10-27
 */
@ConfigurationProperties(prefix = "claim.outbox.relay")
public record OutboxRelayProperties(
        long fixedDelayMs,
        int batchSize,
        int maxRetryCount,
        long retryBaseSeconds,
        long retryMaxSeconds) {
}
