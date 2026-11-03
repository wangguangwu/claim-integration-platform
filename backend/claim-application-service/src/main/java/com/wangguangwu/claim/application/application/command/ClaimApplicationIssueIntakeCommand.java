package com.wangguangwu.claim.application.application.command;

/**
 * 问题件下发命令，来自审核系统的 HTTP 回调请求。
 *
 * @param claimApplicationNo 平台申请编号
 * @param issueNo            问题件编号
 * @param reason             问题原因
 * @param auditor            问题件下发人，沿用 HTTP 请求字段名，传入聚合后记为 raisedBy
 * @param issueContent       问题详情 JSON
 * @author wangguangwu
 * @since 2026-10-31
 */
public record ClaimApplicationIssueIntakeCommand(
        String claimApplicationNo,
        String issueNo,
        String reason,
        String auditor,
        String issueContent
) {
}
