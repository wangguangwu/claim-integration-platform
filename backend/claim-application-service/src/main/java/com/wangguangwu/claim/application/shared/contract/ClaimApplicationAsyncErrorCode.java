package com.wangguangwu.claim.application.shared.contract;

/**
 * 异步链路可对外返回的业务错误码。
 *
 * <p>枚举常量名即对外错误码字符串，HTTP 状态由入站层统一映射。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ClaimApplicationAsyncErrorCode implements BusinessErrorCode {

    /** 异步回调缺少必要字段、字段格式错误或未通过业务校验。 */
    CALLBACK_INVALID,

    /** 回调关联的理赔申请不存在。 */
    CLAIM_APPLICATION_NOT_FOUND,

    /** 异步处理过程中发生未预期的系统故障。 */
    CLAIM_APPLICATION_ASYNC_SYSTEM_ERROR,

    /** 审核签收消息载荷非法，案件号缺失或不符合约定格式。 */
    AUDIT_SIGNED_INVALID,

    /** 审核签收消息携带的租户与申请所属租户不一致。 */
    AUDIT_SIGNED_TENANT_MISMATCH,

    /** 审核签收对应的申请状态不允许完成。 */
    AUDIT_SIGNED_STATUS_INVALID;

    /**
     * 返回对外错误码字符串，与枚举常量名保持一致。
     *
     * @return 对外错误码
     */
    @Override
    public String code() {
        return name();
    }
}
