package com.wangguangwu.claim.application.domain.issue.exception;

/**
 * 问题件链路中的可预期业务异常。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
public class ClaimApplicationIssueException extends RuntimeException {

    private final ClaimApplicationIssueErrorCode errorCode;

    /**
     * 创建包含明确业务错误码的异常。
     *
     * @param errorCode 错误码
     * @param message   面向调用方的错误说明，不得包含敏感请求字段
     */
    public ClaimApplicationIssueException(ClaimApplicationIssueErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建保留原始失败原因的异常，便于排障时追溯真实故障点。
     *
     * @param errorCode 错误码
     * @param message   面向调用方的错误说明，不得包含敏感请求字段
     * @param cause     原始异常
     */
    public ClaimApplicationIssueException(ClaimApplicationIssueErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 创建"问题件命令不合法"业务异常，统一错误码与构造入口。
     *
     * @param message 面向调用方的错误说明，不得包含敏感请求字段
     * @return 业务异常
     */
    public static ClaimApplicationIssueException invalid(String message) {
        return new ClaimApplicationIssueException(ClaimApplicationIssueErrorCode.ISSUE_INTAKE_INVALID, message);
    }

    /**
     * 创建"问题件命令不合法"业务异常并保留解析失败原因。
     *
     * @param message 面向调用方的错误说明，不得包含敏感请求字段
     * @param cause   原始异常
     * @return 业务异常
     */
    public static ClaimApplicationIssueException invalid(String message, Throwable cause) {
        return new ClaimApplicationIssueException(ClaimApplicationIssueErrorCode.ISSUE_INTAKE_INVALID, message, cause);
    }

    /**
     * 获取业务错误码。
     *
     * @return 错误码
     */
    public ClaimApplicationIssueErrorCode getErrorCode() {
        return errorCode;
    }
}
