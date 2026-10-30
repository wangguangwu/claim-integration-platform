package com.wangguangwu.claim.application.domain.claim.port;

import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeLookup;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeSnapshot;

/**
 * 签收判定快照的查询端口，一次查询同时回答"是否可复用"与"是否存在"。
 *
 * <p>实现可以缓存安全状态下的判定结论，但缓存不可用时必须回源数据库并保持同样的结论语义。</p>
 *
 * @author wangguangwu
 * @since 2026-10-30
 */
public interface ClaimApplicationIntakeSnapshotQuery {

    /**
     * 按租户与外部申请编号取得签收判定结论。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @return 可复用、存在但状态可变、或不存在
     */
    ClaimApplicationIntakeLookup lookup(String tenantCode, String externalClaimApplicationNo);

    /**
     * 标记该外部申请已存在且状态可变，供创建成功后覆盖之前的空标记。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     */
    void markMutable(String tenantCode, String externalClaimApplicationNo);

    /**
     * 把存在标记升级为可复用快照，供回源读到安全状态后使用。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @param snapshot              回源观察到的可复用快照
     */
    void markReusable(String tenantCode, String externalClaimApplicationNo, ClaimApplicationIntakeSnapshot snapshot);
}
