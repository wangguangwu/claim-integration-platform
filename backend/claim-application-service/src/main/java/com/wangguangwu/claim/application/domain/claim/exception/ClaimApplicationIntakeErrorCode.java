package com.wangguangwu.claim.application.domain.claim.exception;

/**
 * 统一接入可对外返回的业务错误码。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ClaimApplicationIntakeErrorCode {

    /**
     * 租户编码与协议版本不存在对应的接入流水线。
     */
    TENANT_PROTOCOL_UNSUPPORTED,

    /**
     * 租户编码与协议版本命中多个接入流水线，无法确定唯一处理器。
     */
    TENANT_PROTOCOL_AMBIGUOUS,

    /**
     * 签收请求缺少必要字段、字段格式错误或未通过业务校验。
     */
    CLAIM_APPLICATION_INTAKE_INVALID,

    /**
     * 同一申请正在签收处理中，未能在等待时限内获得处理资格。
     */
    CLAIM_APPLICATION_INTAKE_BUSY,

    /**
     * 签收过程中发生未预期的系统故障。
     */
    CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR,

    /**
     * 审核交付所关联的理赔申请不存在。
     */
    AUDIT_DELIVERY_APPLICATION_NOT_FOUND
}
