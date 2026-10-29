package com.wangguangwu.claim.application.usecase.audit.result;

/**
 * 审核交付处理结果。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
public enum AuditDeliveryOutcome {

    /**
     * 已交付审核系统，并写入完整行为组与消费登记。
     */
    DELIVERED,

    /**
     * 申请已不在等待审核签收状态，按重复事件记录复用轨迹。
     */
    REUSED,

    /**
     * 同一事件已由其他消费者登记，直接跳过。
     */
    SKIPPED
}
