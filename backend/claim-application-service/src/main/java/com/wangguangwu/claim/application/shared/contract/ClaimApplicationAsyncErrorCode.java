package com.wangguangwu.claim.application.shared.contract;

/**
 * 异步链路可对外返回的业务错误码。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ClaimApplicationAsyncErrorCode {

    CALLBACK_INVALID,
    CLAIM_APPLICATION_NOT_FOUND,
    CLAIM_APPLICATION_ASYNC_SYSTEM_ERROR
}
