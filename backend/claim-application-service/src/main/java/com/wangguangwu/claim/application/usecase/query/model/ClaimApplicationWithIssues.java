package com.wangguangwu.claim.application.usecase.query.model;

import java.util.List;

/**
 * 申请摘要及其全部问题件。
 *
 * @param application 申请摘要
 * @param issues      全部问题件，含已关闭，按创建时间倒序排列
 * @author wangguangwu
 * @since 2026-11-03
 */
public record ClaimApplicationWithIssues(ClaimApplicationSummary application, List<IssueView> issues) {
}
