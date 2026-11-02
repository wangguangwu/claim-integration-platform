package com.wangguangwu.claim.application.usecase.issue.result;

import java.time.LocalDateTime;

/**
 * 按案件号查询问题件状态的结果。
 *
 * @param claimApplicationNo 平台申请编号
 * @param caseNo             案件号
 * @param hasOpenIssue       是否存在未关闭问题件
 * @param issueNo            未关闭问题件编号，无问题件时为空
 * @param reason             未关闭问题件原因，无问题件时为空
 * @param raisedAt           问题件下发时间，无问题件时为空
 * @author wangguangwu
 * @since 2026-11-02
 */
public record ClaimApplicationIssueQueryResult(String claimApplicationNo, String caseNo, boolean hasOpenIssue,
                                               String issueNo, String reason, LocalDateTime raisedAt) {
}
