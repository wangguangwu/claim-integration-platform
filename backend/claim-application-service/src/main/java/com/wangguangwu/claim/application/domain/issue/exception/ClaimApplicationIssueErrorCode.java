package com.wangguangwu.claim.application.domain.issue.exception;

/**
 * 问题件链路可对外返回的业务错误码。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
public enum ClaimApplicationIssueErrorCode {

    /** 申请不存在，下发问题件时申请编号未找到。 */
    ISSUE_APPLICATION_NOT_FOUND,

    /** 申请状态不合法，下发问题时申请状态不是 WAIT_AUDIT。 */
    ISSUE_APPLICATION_STATUS_INVALID,

    /** 已有未关闭问题件，同一申请同一时间只允许一个 OPEN 状态问题件。 */
    ISSUE_ALREADY_OPEN,

    /** 问题件不存在，关闭时问题件编号未找到。 */
    ISSUE_NOT_FOUND,

    /** 问题件命令不合法，必填字段缺失或超长。 */
    ISSUE_INTAKE_INVALID,

    /** 问题件系统异常，未预期的系统错误。 */
    ISSUE_SYSTEM_ERROR
}
