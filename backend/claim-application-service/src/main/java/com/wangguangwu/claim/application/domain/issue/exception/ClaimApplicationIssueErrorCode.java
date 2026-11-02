package com.wangguangwu.claim.application.domain.issue.exception;

import com.wangguangwu.claim.application.shared.contract.BusinessErrorCode;

/**
 * 问题件链路可对外返回的业务错误码。
 *
 * <p>枚举常量名即对外错误码字符串，HTTP 状态由入站层统一映射。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
public enum ClaimApplicationIssueErrorCode implements BusinessErrorCode {

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
    ISSUE_SYSTEM_ERROR,

    /** 案件关系不存在，按案件号反查不到平台申请编号。 */
    CASE_RELATION_NOT_FOUND;

    /**
     * 返回对外错误码字符串，与枚举常量名保持一致。
     *
     * @return 对外错误码
     */
    @Override
    public String code() {
        return name();
    }
}
