package com.wangguangwu.claim.application.interfaces.intake.web.issue;

/**
 * 问题件下发成功响应数据。
 *
 * @param issueNo            问题件编号
 * @param claimApplicationNo 平台申请编号
 * @param created            是否新创建
 * @author wangguangwu
 * @since 2026-10-31
 */
public record ClaimApplicationIssueIntakeResponse(String issueNo, String claimApplicationNo, boolean created) {
}
