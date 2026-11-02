package com.wangguangwu.claim.application.interfaces.issue.web;

/**
 * 问题件关闭成功响应数据。
 *
 * @param issueNo 问题件编号
 * @param closed  是否新关闭
 * @author wangguangwu
 * @since 2026-10-31
 */
public record ClaimApplicationIssueCloseResponse(String issueNo, boolean closed) {
}
