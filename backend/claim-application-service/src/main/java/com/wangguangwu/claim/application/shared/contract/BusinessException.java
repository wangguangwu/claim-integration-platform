package com.wangguangwu.claim.application.shared.contract;

import lombok.Getter;

/**
 * 统一业务异常，表达可预期的业务拒绝。
 *
 * <p>只携带业务错误码与面向调用方的错误说明，不依赖 Spring Web 类型，
 * 领域、用例、基础设施与入站适配均可抛出；HTTP 状态由入站层按错误码映射。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Getter
public class BusinessException extends RuntimeException {

    private final BusinessErrorCode errorCode;

    /**
     * 创建包含明确业务错误码的异常。
     *
     * @param errorCode 业务错误码
     * @param message   面向调用方的错误说明，不得包含敏感请求字段
     */
    public BusinessException(BusinessErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建保留原始失败原因的异常，便于排障时追溯真实故障点。
     *
     * @param errorCode 业务错误码
     * @param message   面向调用方的错误说明，不得包含敏感请求字段
     * @param cause     原始异常
     */
    public BusinessException(BusinessErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
