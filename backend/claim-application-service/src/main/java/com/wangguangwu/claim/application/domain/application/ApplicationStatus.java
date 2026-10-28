package com.wangguangwu.claim.application.domain.application;

/**
 * 理赔申请生命周期状态。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ApplicationStatus {

    PENDING_ACCEPT,
    ACCEPTED,
    ACCEPT_FAILED,
    UNDERWRITING,
    WAIT_AUDIT,
    ISSUE,
    COMPLETED,
    REVOKED
}
