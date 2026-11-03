package com.wangguangwu.claim.application.domain.claim.exception;

import com.wangguangwu.claim.application.shared.contract.BusinessErrorCode;

/**
 * 理赔申请聚合自身不变式拒绝时使用的业务错误码。
 *
 * <p>与签收、问题件等链路错误码分开：这些错误由聚合状态机直接判定，
 * 不依赖具体入口，HTTP 状态仍由入站层统一映射。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public enum ClaimApplicationErrorCode implements BusinessErrorCode {

    /** 申请当前状态不允许执行该业务迁移。 */
    CLAIM_APPLICATION_STATUS_INVALID,

    /** 材料处理结果与当前待处理材料无法一一对应，存在重复、缺失或未知结果。 */
    CLAIM_APPLICATION_MATERIAL_RESULT_INVALID,

    /** 材料自身状态不允许写入处理结果。 */
    CLAIM_APPLICATION_MATERIAL_STATUS_INVALID;

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
