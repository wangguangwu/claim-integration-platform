package com.wangguangwu.claim.application.application.query.model;

import java.time.LocalDateTime;

/**
 * 案件关联查询视图。
 *
 * @param caseNo    案件号
 * @param createdAt 创建时间
 * @author wangguangwu
 * @since 2026-11-03
 */
public record CaseView(String caseNo, LocalDateTime createdAt) {
}
