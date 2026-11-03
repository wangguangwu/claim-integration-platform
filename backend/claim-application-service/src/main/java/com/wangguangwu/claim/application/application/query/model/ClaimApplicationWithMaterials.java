package com.wangguangwu.claim.application.application.query.model;

import java.util.List;

/**
 * 申请摘要及其未删除材料。
 *
 * @param application 申请摘要
 * @param materials   未删除材料，按创建顺序排列
 * @author wangguangwu
 * @since 2026-11-03
 */
public record ClaimApplicationWithMaterials(ClaimApplicationSummary application, List<MaterialView> materials) {
}
