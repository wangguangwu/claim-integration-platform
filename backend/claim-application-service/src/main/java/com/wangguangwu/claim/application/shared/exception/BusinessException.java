package com.wangguangwu.claim.application.shared.exception;

/**
 * 业务异常共同基类，表达可预期的业务拒绝。
 *
 * <p>只携带面向调用方的错误说明，不依赖 Spring Web 类型；HTTP 状态由入站层统一按失败处理。
 * 领域层抛出 {@code DomainException}，用例层抛出 {@code ApplicationException}，
 * 基础设施与入站适配在自身边界内直接使用本类型或其子类。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public class BusinessException extends RuntimeException {

    /**
     * 创建业务异常。
     *
     * @param message 面向调用方的错误说明，不得包含敏感请求字段
     */
    public BusinessException(String message) {
        super(message);
    }

    /**
     * 创建保留原始失败原因的业务异常，便于排障时追溯真实故障点。
     *
     * @param message 面向调用方的错误说明，不得包含敏感请求字段
     * @param cause   原始异常
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
