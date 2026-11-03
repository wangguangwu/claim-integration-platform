package com.wangguangwu.claim.application.shared.exception;

/**
 * 领域业务异常，表达聚合或领域服务拒绝执行的业务事实。
 *
 * <p>领域层只抛出本类型；错误码与消息由具体业务规则提供，对外状态映射仍由入站层统一处理。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public class DomainException extends BusinessException {

    /**
     * 创建领域业务异常。
     *
     * @param errorCode 业务错误码
     * @param message   面向调用方的错误说明，不得包含敏感请求字段
     */
    public DomainException(BusinessErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建保留原始失败原因的领域业务异常。
     *
     * @param errorCode 业务错误码
     * @param message   面向调用方的错误说明，不得包含敏感请求字段
     * @param cause     原始异常
     */
    public DomainException(BusinessErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
