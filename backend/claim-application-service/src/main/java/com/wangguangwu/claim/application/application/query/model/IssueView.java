package com.wangguangwu.claim.application.application.query.model;

import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssueStatus;
import java.time.LocalDateTime;

/**
 * 问题件查询视图。
 *
 * @param issueNo      问题件编号
 * @param reason       问题原因
 * @param raisedBy     问题件下发人
 * @param issueContent 问题详情 JSON
 * @param status       问题件状态
 * @param closedBy     问题件关闭人，未关闭时为空
 * @param closedAt     关闭时间，未关闭时为空
 * @param createdAt    创建时间
 * @author wangguangwu
 * @since 2026-11-03
 */
public record IssueView(String issueNo, String reason, String raisedBy, String issueContent,
                        ClaimApplicationIssueStatus status, String closedBy, LocalDateTime closedAt,
                        LocalDateTime createdAt) {
}
