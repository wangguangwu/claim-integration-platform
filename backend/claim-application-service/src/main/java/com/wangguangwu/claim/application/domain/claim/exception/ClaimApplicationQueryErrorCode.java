package com.wangguangwu.claim.application.domain.claim.exception;

import com.wangguangwu.claim.application.shared.exception.BusinessErrorCode;

/**
 * 理赔申请详情查询链路可对外返回的业务错误码。
 *
 * <p>枚举常量名即对外错误码字符串，HTTP 状态由入站层统一映射；不复用异步链路的
 * {@code CLAIM_APPLICATION_NOT_FOUND}，避免不同链路的对外错误码字符串重复。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public enum ClaimApplicationQueryErrorCode implements BusinessErrorCode {

    /** 申请不存在或不属于当前租户。 */
    CLAIM_APPLICATION_QUERY_NOT_FOUND,

    /** 查询请求缺少必要上下文或参数。 */
    CLAIM_APPLICATION_QUERY_INVALID,

    /** 查询过程中发生未预期的系统故障。 */
    CLAIM_APPLICATION_QUERY_SYSTEM_ERROR;

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
