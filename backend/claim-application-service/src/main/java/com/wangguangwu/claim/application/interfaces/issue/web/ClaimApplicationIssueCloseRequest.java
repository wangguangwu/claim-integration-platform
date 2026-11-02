package com.wangguangwu.claim.application.interfaces.issue.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 问题件关闭 HTTP 请求体。
 *
 * @param auditor 处理人
 * @author wangguangwu
 * @since 2026-10-31
 */
public record ClaimApplicationIssueCloseRequest(
        @NotBlank(message = "处理人不能为空")
        @Size(max = 128, message = "处理人不能超过 128 字符")
        String auditor
) {
}
