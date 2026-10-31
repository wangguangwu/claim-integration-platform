package com.wangguangwu.claim.application.usecase.issue.result;

/**
 * 问题件下发结果。
 *
 * @param issueNo            问题件编号
 * @param claimApplicationNo 平台申请编号
 * @param created            是否新创建（false 表示幂等命中）
 * @author wangguangwu
 * @since 2026-10-31
 */
public record ClaimApplicationIssueIntakeResult(String issueNo, String claimApplicationNo, boolean created) {
}
