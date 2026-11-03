package com.wangguangwu.claim.application.application.process;

/**
 * 审核签收处理结果。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public enum AuditSignedOutcome {

    /**
     * 本次签收把申请从等待审核迁移到已完成。
     */
    COMPLETED,

    /**
     * 申请已完成，本次补充登记了新的案件号。
     */
    RECORDED,

    /**
     * 申请已完成，但本次全部案件号已登记过。
     */
    REUSED,

    /**
     * 同一事件已由本消费组登记，直接跳过。
     */
    SKIPPED
}
