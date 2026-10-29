package com.wangguangwu.claim.application.domain.integration.compensation;

import java.time.Duration;

/**
 * 补偿策略端口，单轮处理条数与各状态的滞留判定阈值由部署配置决定。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
public interface ApplicationCompensationPolicy {

    /**
     * 单轮补偿处理的申请条数。
     *
     * @return 批量大小
     */
    int batchSize();

    /**
     * 发送中事件判定为滞留的时长，覆盖投递进程崩溃场景。
     *
     * @return 滞留阈值
     */
    Duration outboxSendingTimeout();

    /**
     * 已受理申请判定为分流滞留的时长。
     *
     * @return 滞留阈值
     */
    Duration acceptedStallTimeout();

    /**
     * 承保中申请判定为结果缺失的时长。
     *
     * @return 滞留阈值
     */
    Duration underwritingTimeout();
}
