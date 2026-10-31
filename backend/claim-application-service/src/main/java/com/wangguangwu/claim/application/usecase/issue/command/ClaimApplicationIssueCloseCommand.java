package com.wangguangwu.claim.application.usecase.issue.command;

/**
 * 问题件关闭命令，来自审核系统的 HTTP 回调请求。
 *
 * @param issueNo 问题件编号
 * @param auditor 处理人
 * @author wangguangwu
 * @since 2026-10-31
 */
public record ClaimApplicationIssueCloseCommand(String issueNo, String auditor) {
}
