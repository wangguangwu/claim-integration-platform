package com.wangguangwu.claim.application.domain.issue.event;

import java.time.LocalDateTime;

/**
 * 问题件下发领域事件。
 *
 * <p>当审核系统下发问题件且问题件创建成功后发布，可用于通知租户、记录审计日志等异步场景。</p>
 *
 * @param issueNo            问题件编号
 * @param claimApplicationNo 平台申请编号
 * @param reason             问题原因
 * @param auditor            审核人员
 * @param raisedAt           下发时间
 * @author wangguangwu
 * @since 2026-10-31
 */
public record IssueRaisedEvent(
        String issueNo,
        String claimApplicationNo,
        String reason,
        String auditor,
        LocalDateTime raisedAt
) implements IssueDomainEvent {
}
