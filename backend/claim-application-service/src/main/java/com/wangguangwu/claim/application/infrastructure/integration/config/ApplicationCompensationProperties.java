package com.wangguangwu.claim.application.infrastructure.integration.config;

import com.wangguangwu.claim.application.domain.integration.compensation.ApplicationCompensationPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 补偿策略的配置适配器，批量、间隔与滞留阈值全部来自配置。
 *
 * <p>调度间隔由 {@code @Scheduled} 占位符直接读取，不经过领域策略端口。</p>
 *
 * @param fixedDelayMs         补偿调度间隔
 * @param batchSize            单次处理的申请条数
 * @param outboxSendingTimeout 发送中事件的滞留判定阈值
 * @param acceptedStallTimeout 已受理申请的分流滞留判定阈值
 * @param underwritingTimeout  承保中申请的结果查询阈值
 * @author wangguangwu
 * @since 2026-10-27
 */
@ConfigurationProperties(prefix = "claim.compensation")
public record ApplicationCompensationProperties(
        long fixedDelayMs,
        int batchSize,
        Duration outboxSendingTimeout,
        Duration acceptedStallTimeout,
        Duration underwritingTimeout) implements ApplicationCompensationPolicy {
}
