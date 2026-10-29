package com.wangguangwu.claim.application.domain.integration.outbox.port;

import java.time.Duration;

/**
 * Outbox 投递策略端口，批量、投递次数上限与退避时长由部署配置决定。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
public interface OutboxRelayPolicy {

    /**
     * 单次投递处理的最大事件数。
     *
     * @return 批量大小
     */
    int batchSize();

    /**
     * 允许的最大投递次数，达到后置为失败等待人工处理。
     *
     * @return 最大投递次数
     */
    int maxRetryCount();

    /**
     * 第 attemptCount 次投递失败后允许再次投递前的等待时长。
     *
     * @param attemptCount 失败后的累计投递次数
     * @return 退避时长
     */
    Duration retryDelay(int attemptCount);
}
