package com.wangguangwu.claim.application.domain.claim.exception;

/**
 * 统一接入可对外返回的业务错误码。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ApplicationIntakeErrorCode {

    TENANT_PROTOCOL_UNSUPPORTED,
    TENANT_PROTOCOL_AMBIGUOUS,
    APPLICATION_INTAKE_INVALID,
    APPLICATION_INTAKE_BUSY,
    APPLICATION_INTAKE_SYSTEM_ERROR
}
