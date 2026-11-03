package com.wangguangwu.claim.application.application.port.model;

import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;

/**
 * 签收判定快照，记录"该外部申请已完成受理、可直接复用"这一结论。
 *
 * <p>可复用状态由 {@link ClaimApplicationStatus#isReusableAfterIntake()} 回答。状态并非严格单调：
 * {@code WAIT_AUDIT} 可因问题件回退到 {@code ISSUE}，因此可复用结论依赖问题件下发时主动失效来维持，
 * 而不是依赖状态只进不退。</p>
 *
 * @param claimApplicationNo 平台申请编号，重复签收时直接返回给租户
 * @param status             快照时刻的平台状态
 * @author wangguangwu
 * @since 2026-10-30
 */
public record ClaimApplicationIntakeSnapshot(String claimApplicationNo, ClaimApplicationStatus status) {

    /**
     * 校验快照只在可复用状态内构造，越界即视为编程错误。
     *
     * @param claimApplicationNo 平台申请编号
     * @param status             平台状态
     */
    public ClaimApplicationIntakeSnapshot {
        if (claimApplicationNo == null || claimApplicationNo.isBlank()) {
            throw new IllegalArgumentException("签收判定快照必须携带平台申请编号");
        }
        if (status == null || !status.isReusableAfterIntake()) {
            throw new IllegalArgumentException("签收判定快照不接受可变状态: " + status);
        }
    }
}
