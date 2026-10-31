package com.wangguangwu.claim.application.domain.issue.event;

import java.time.LocalDateTime;

/**
 * 问题件关闭领域事件。
 *
 * <p>当审核系统关闭问题件且问题件状态变更为 CLOSED 后发布，可用于通知租户、记录审计日志等异步场景。</p>
 *
 * @param issueNo            问题件编号
 * @param claimApplicationNo 平台申请编号
 * @param auditor            处理人
 * @param closedAt           关闭时间
 * @author wangguangwu
 * @since 2026-10-31
 */
public record IssueClosedEvent(
        String issueNo,
        String claimApplicationNo,
        String auditor,
        LocalDateTime closedAt
) {
}
