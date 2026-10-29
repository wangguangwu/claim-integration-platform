package com.wangguangwu.claim.application.domain.claim.exception;

/**
 * 统一接入过程中的可预期业务异常。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class ApplicationIntakeException extends RuntimeException {

    private final ApplicationIntakeErrorCode errorCode;

    /**
     * 创建包含明确业务错误码的异常。
     *
     * @param errorCode 错误码
     * @param message 面向调用方的错误说明，不得包含敏感请求字段
     */
    public ApplicationIntakeException(ApplicationIntakeErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建"签收命令不合法"业务异常，统一错误码与构造入口。
     *
     * @param message 面向调用方的错误说明，不得包含敏感请求字段
     * @return 业务异常
     */
    public static ApplicationIntakeException invalid(String message) {
        return new ApplicationIntakeException(ApplicationIntakeErrorCode.APPLICATION_INTAKE_INVALID, message);
    }

    /**
     * 获取业务错误码。
     *
     * @return 错误码
     */
    public ApplicationIntakeErrorCode getErrorCode() {
        return errorCode;
    }
}
