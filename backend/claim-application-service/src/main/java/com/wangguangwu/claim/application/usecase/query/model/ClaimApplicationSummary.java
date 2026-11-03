package com.wangguangwu.claim.application.usecase.query.model;

import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import java.time.LocalDateTime;

/**
 * 理赔申请详情查询的申请摘要视图。
 *
 * @param tenantCode             租户编码
 * @param claimApplicationNo     平台申请编号
 * @param externalClaimApplicationNo 租户侧申请编号
 * @param status                 当前生命周期状态
 * @param claimApplicationAttempt 受理尝试次数
 * @param createdAt              创建时间
 * @param updatedAt              更新时间
 * @param completedAt            审核完成时间，未完成时为空
 * @author wangguangwu
 * @since 2026-11-03
 */
public record ClaimApplicationSummary(String tenantCode, String claimApplicationNo,
                                      String externalClaimApplicationNo, ClaimApplicationStatus status,
                                      Integer claimApplicationAttempt, LocalDateTime createdAt,
                                      LocalDateTime updatedAt, LocalDateTime completedAt) {
}
