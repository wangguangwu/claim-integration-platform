package com.wangguangwu.claim.application.support.mock.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模拟承保配置，仅在模拟开关开启时生效。
 *
 * @param notifyOnComplete 自动完成后是否发送承保完成通知；关闭后用于验证补偿查询链路
 * @param completionDelay  自动承保从受理到完成的模拟耗时
 * @author wangguangwu
 * @since 2026-10-27
 */
@ConfigurationProperties(prefix = "claim.mock.underwriting")
public record MockUnderwritingProperties(boolean notifyOnComplete, Duration completionDelay) {
}
