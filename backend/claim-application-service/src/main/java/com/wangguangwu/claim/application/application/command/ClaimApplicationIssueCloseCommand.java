package com.wangguangwu.claim.application.application.command;

/**
 * 问题件关闭命令，来自审核系统的 HTTP 回调请求。
 *
 * @param issueNo 问题件编号
 * @param auditor 问题件关闭人，沿用 HTTP 请求字段名，传入聚合后记为 closedBy
 * @author wangguangwu
 * @since 2026-10-31
 */
public record ClaimApplicationIssueCloseCommand(String issueNo, String auditor) {
}
