package com.wangguangwu.claim.application.domain.claimcase.port;

import com.wangguangwu.claim.application.domain.claimcase.ClaimCaseRelation;
import java.util.List;
import java.util.Optional;

/**
 * 案件关联的持久化端口。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public interface ClaimCaseRelationRepository {

    /**
     * 查询申请已登记的全部案件号。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 已登记案件号，无记录时为空列表
     */
    List<String> findCaseNos(String claimApplicationNo);

    /**
     * 按租户与案件号反查平台申请编号。
     *
     * @param tenantCode 租户编码
     * @param caseNo     案件号
     * @return 平台申请编号；关联不存在时为空
     */
    Optional<String> findClaimApplicationNo(String tenantCode, String caseNo);

    /**
     * 批量插入案件关联，唯一键冲突时向上抛出。
     *
     * @param relations 待插入关联，空集合直接返回
     */
    void insertAll(List<ClaimCaseRelation> relations);
}
