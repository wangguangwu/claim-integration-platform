package com.wangguangwu.claim.application.application.query.model;

import java.util.List;

/**
 * 申请摘要及其案件关联。
 *
 * @param application 申请摘要
 * @param cases       案件关联，按创建时间升序排列
 * @author wangguangwu
 * @since 2026-11-03
 */
public record ClaimApplicationWithCases(ClaimApplicationSummary application, List<CaseView> cases) {
}
