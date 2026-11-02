package com.wangguangwu.claim.application.usecase.audit.result;

/**
 * 审核签收处理结果。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public enum AuditSignedOutcome {

    /**
     * 至少登记了一个新的案件号。
     */
    RECORDED,

    /**
     * 申请存在，但本次全部案件号已登记过。
     */
    REUSED,

    /**
     * 同一事件已由本消费组登记，直接跳过。
     */
    SKIPPED
}
