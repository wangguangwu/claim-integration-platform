package com.wangguangwu.claim.application.usecase.intake.result;

/**
 * 申请签收结果类型。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ClaimApplicationIntakeDisposition {

    /** 首次签收成功，创建新的理赔申请。 */
    CREATED,

    /** 同一请求已完成签收，直接复用既有申请。 */
    REUSED,

    /** 原申请处于受理失败状态，已基于本次请求重新申请。 */
    REAPPLIED
}
