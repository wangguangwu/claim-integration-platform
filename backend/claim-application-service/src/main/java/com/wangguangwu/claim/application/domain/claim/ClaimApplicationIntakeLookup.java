package com.wangguangwu.claim.application.domain.claim;

/**
 * 签收判定查询结果，一次查询同时回答"是否可复用"与"是否存在"。
 *
 * <p>可复用结论必须携带快照，其余结论不携带——避免调用方把"存在但状态可变"误当成可直接复用。</p>
 *
 * @param kind     判定结论种类
 * @param snapshot 可复用时的签收判定快照，其余结论为 null
 * @author wangguangwu
 * @since 2026-10-30
 */
public record ClaimApplicationIntakeLookup(ClaimApplicationIntakeLookupKind kind,
                                           ClaimApplicationIntakeSnapshot snapshot) {

    /**
     * 校验结论与快照的搭配关系。
     *
     * @param kind     判定结论种类
     * @param snapshot 签收判定快照
     */
    public ClaimApplicationIntakeLookup {
        if (kind == null) {
            throw new IllegalArgumentException("签收判定结论不能为空");
        }
        if (kind == ClaimApplicationIntakeLookupKind.REUSABLE && snapshot == null) {
            throw new IllegalArgumentException("可复用判定必须携带签收判定快照");
        }
        if (kind != ClaimApplicationIntakeLookupKind.REUSABLE && snapshot != null) {
            throw new IllegalArgumentException("非可复用判定不得携带签收判定快照");
        }
    }

    /**
     * 构造可直接复用的判定结果。
     *
     * @param snapshot 签收判定快照
     * @return 判定结果
     */
    public static ClaimApplicationIntakeLookup reusable(ClaimApplicationIntakeSnapshot snapshot) {
        return new ClaimApplicationIntakeLookup(ClaimApplicationIntakeLookupKind.REUSABLE, snapshot);
    }

    /**
     * 构造"存在但状态可变"的判定结果。
     *
     * @return 判定结果
     */
    public static ClaimApplicationIntakeLookup existing() {
        return new ClaimApplicationIntakeLookup(ClaimApplicationIntakeLookupKind.EXISTING, null);
    }

    /**
     * 构造"不存在"的判定结果。
     *
     * @return 判定结果
     */
    public static ClaimApplicationIntakeLookup absent() {
        return new ClaimApplicationIntakeLookup(ClaimApplicationIntakeLookupKind.ABSENT, null);
    }
}
