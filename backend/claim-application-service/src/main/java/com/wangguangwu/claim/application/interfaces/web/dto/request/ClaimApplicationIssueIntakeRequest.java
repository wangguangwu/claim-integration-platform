package com.wangguangwu.claim.application.interfaces.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 问题件下发 HTTP 请求体。
 *
 * @param claimApplicationNo 平台申请编号
 * @param issueNo            问题件编号
 * @param reason             问题原因
 * @param auditor            审核人员
 * @param issueContent       问题详情 JSON
 * @author wangguangwu
 * @since 2026-10-31
 */
public record ClaimApplicationIssueIntakeRequest(
        @NotBlank(message = "平台申请编号不能为空")
        String claimApplicationNo,

        @NotBlank(message = "问题件编号不能为空")
        @Size(max = 64, message = "问题件编号不能超过 64 字符")
        String issueNo,

        @NotBlank(message = "问题原因不能为空")
        @Size(max = 512, message = "问题原因不能超过 512 字符")
        String reason,

        @NotBlank(message = "审核人员不能为空")
        @Size(max = 128, message = "审核人员不能超过 128 字符")
        String auditor,

        @NotBlank(message = "问题详情不能为空")
        String issueContent
) {
}
