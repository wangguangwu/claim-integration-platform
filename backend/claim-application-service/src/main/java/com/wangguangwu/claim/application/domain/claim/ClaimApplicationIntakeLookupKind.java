package com.wangguangwu.claim.application.domain.claim;

/**
 * 签收判定结论种类，决定用例是否需要读取聚合与生成编号。
 *
 * @author wangguangwu
 * @since 2026-10-30
 */
public enum ClaimApplicationIntakeLookupKind {

    /**
     * 已过受理且状态单调，可直接按重复请求复用，无需读取聚合。
     */
    REUSABLE,

    /**
     * 申请存在但状态可变，必须读取聚合并由状态决定复用或重新申请。
     */
    EXISTING,

    /**
     * 申请不存在，可以生成编号并创建。
     */
    ABSENT
}
