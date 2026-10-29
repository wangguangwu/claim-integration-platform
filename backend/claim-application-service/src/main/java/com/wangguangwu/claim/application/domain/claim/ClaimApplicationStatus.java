package com.wangguangwu.claim.application.domain.claim;

/**
 * 理赔申请生命周期状态，只覆盖当前已实现的接入到等待审核链路。
 *
 * <p>问题件、结案与撤销状态属于审核能力，未实现前不保留枚举值。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ClaimApplicationStatus {

    PENDING_ACCEPT,
    ACCEPTED,
    ACCEPT_FAILED,
    UNDERWRITING,
    WAIT_AUDIT
}
