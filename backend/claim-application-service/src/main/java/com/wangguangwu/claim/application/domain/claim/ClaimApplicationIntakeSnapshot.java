package com.wangguangwu.claim.application.domain.claim;

import java.util.EnumSet;
import java.util.Set;

/**
 * 签收判定快照，记录"该外部申请已完成受理、可直接复用"这一单调事实。
 *
 * <p>只有 {@link ClaimApplicationStatus#ACCEPTED}、{@link ClaimApplicationStatus#UNDERWRITING}、
 * {@link ClaimApplicationStatus#WAIT_AUDIT} 三个状态可以构造快照：它们在状态机里只会继续向前，
 * 不会回到 {@link ClaimApplicationStatus#ACCEPT_FAILED}，因此缓存无需承担失效责任。</p>
 *
 * @param claimApplicationNo 平台申请编号，重复签收时直接返回给租户
 * @param status             快照时刻的平台状态
 * @author wangguangwu
 * @since 2026-10-30
 */
public record ClaimApplicationIntakeSnapshot(String claimApplicationNo, ClaimApplicationStatus status) {

    private static final Set<ClaimApplicationStatus> REUSABLE_STATUSES = EnumSet.of(
            ClaimApplicationStatus.ACCEPTED,
            ClaimApplicationStatus.UNDERWRITING,
            ClaimApplicationStatus.WAIT_AUDIT);

    /**
     * 校验快照只在安全状态集合内构造，越界即视为编程错误。
     *
     * @param claimApplicationNo 平台申请编号
     * @param status             平台状态
     */
    public ClaimApplicationIntakeSnapshot {
        if (claimApplicationNo == null || claimApplicationNo.isBlank()) {
            throw new IllegalArgumentException("签收判定快照必须携带平台申请编号");
        }
        if (!isReusableStatus(status)) {
            throw new IllegalArgumentException("签收判定快照不接受可变状态: " + status);
        }
    }

    /**
     * 判断状态是否属于签收可直接复用的安全集合。
     *
     * @param status 平台状态
     * @return 属于安全集合时为 true
     */
    public static boolean isReusableStatus(ClaimApplicationStatus status) {
        return status != null && REUSABLE_STATUSES.contains(status);
    }
}
