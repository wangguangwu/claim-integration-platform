package com.wangguangwu.claim.application.usecase.underwriting.result;

/**
 * 承保完成通知处理结果。
 *
 * @param claimApplicationNo 平台申请编号
 * @param disposition   处理结果类型
 * @author wangguangwu
 * @since 2026-10-27
 */
public record UnderwritingCallbackResult(String claimApplicationNo, UnderwritingCallbackDisposition disposition) {
}
