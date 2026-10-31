package com.wangguangwu.claim.application.usecase.issue.result;

/**
 * 问题件关闭结果。
 *
 * @param issueNo 问题件编号
 * @param closed  是否新关闭（false 表示幂等命中）
 * @author wangguangwu
 * @since 2026-10-31
 */
public record ClaimApplicationIssueCloseResult(String issueNo, boolean closed) {
}
