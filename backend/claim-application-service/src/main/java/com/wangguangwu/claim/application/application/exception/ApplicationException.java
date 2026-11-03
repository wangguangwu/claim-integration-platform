package com.wangguangwu.claim.application.application.exception;

import com.wangguangwu.claim.application.shared.exception.BusinessException;

/**
 * 应用业务异常，表达用例编排阶段拒绝执行的业务事实。
 *
 * <p>用例层只抛出本类型；聚合内部规则仍由领域层以领域异常拒绝，入站层统一捕获共同基类。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public class ApplicationException extends BusinessException {

    /**
     * 创建应用业务异常。
     *
     * @param message 面向调用方的错误说明，不得包含敏感请求字段
     */
    public ApplicationException(String message) {
        super(message);
    }

    /**
     * 创建保留原始失败原因的应用业务异常。
     *
     * @param message 面向调用方的错误说明，不得包含敏感请求字段
     * @param cause   原始异常
     */
    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
