package com.wangguangwu.claim.application.domain.claim;

/**
 * 理赔申请生命周期状态，覆盖接入、审核与问题件链路。
 *
 * <p>结案与撤销状态属于后续审核能力，未实现前不保留枚举值。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ClaimApplicationStatus {

    PENDING_ACCEPT,
    ACCEPTED,
    ACCEPT_FAILED,
    UNDERWRITING,
    WAIT_AUDIT,
    ISSUE
}
