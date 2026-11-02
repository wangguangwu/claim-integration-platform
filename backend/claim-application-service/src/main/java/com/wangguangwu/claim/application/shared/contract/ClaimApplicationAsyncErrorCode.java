package com.wangguangwu.claim.application.shared.contract;

/**
 * 异步链路可对外返回的业务错误码。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ClaimApplicationAsyncErrorCode {

    /** 异步回调缺少必要字段、字段格式错误或未通过业务校验。 */
    CALLBACK_INVALID,

    /** 回调关联的理赔申请不存在。 */
    CLAIM_APPLICATION_NOT_FOUND,

    /** 异步处理过程中发生未预期的系统故障。 */
    CLAIM_APPLICATION_ASYNC_SYSTEM_ERROR
}
