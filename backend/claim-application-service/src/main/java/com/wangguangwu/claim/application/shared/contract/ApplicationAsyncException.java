package com.wangguangwu.claim.application.shared.contract;

/**
 * 异步链路中的可预期业务异常。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class ApplicationAsyncException extends RuntimeException {

    private final ApplicationAsyncErrorCode errorCode;

    /**
     * 创建包含明确业务错误码的异常。
     *
     * @param errorCode 错误码
     * @param message   面向调用方的错误说明，不得包含敏感请求字段
     */
    public ApplicationAsyncException(ApplicationAsyncErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 获取业务错误码。
     *
     * @return 错误码
     */
    public ApplicationAsyncErrorCode getErrorCode() {
        return errorCode;
    }
}
