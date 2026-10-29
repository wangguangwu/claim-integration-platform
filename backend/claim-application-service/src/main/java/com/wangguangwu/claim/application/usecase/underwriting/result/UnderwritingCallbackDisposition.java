package com.wangguangwu.claim.application.usecase.underwriting.result;

/**
 * 承保完成通知的处理结果。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum UnderwritingCallbackDisposition {

    /**
     * 通知有效，申请已推进到等待审核签收。
     */
    COMPLETED,

    /**
     * 通知重复、迟到或状态不匹配，未改变业务数据。
     */
    REUSED
}
