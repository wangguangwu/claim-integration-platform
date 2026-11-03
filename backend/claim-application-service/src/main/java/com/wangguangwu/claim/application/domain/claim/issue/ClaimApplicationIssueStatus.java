package com.wangguangwu.claim.application.domain.claim.issue;

/**
 * 问题件处理状态。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
public enum ClaimApplicationIssueStatus {

    /** 未关闭，等待租户补材。 */
    OPEN,

    /** 已关闭，租户补材完成且审核系统确认。 */
    CLOSED
}
