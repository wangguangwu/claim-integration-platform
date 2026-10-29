package com.wangguangwu.claim.application.interfaces.underwriting.web;

/**
 * 承保完成回调报文，只携带平台申请编号。
 *
 * @param applicationNo 平台申请编号
 * @author wangguangwu
 * @since 2026-10-27
 */
public record UnderwritingCallbackRequest(String applicationNo) {
}
