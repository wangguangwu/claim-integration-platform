package com.wangguangwu.claim.application.infrastructure.claim.persistence;

import com.wangguangwu.claim.application.domain.claim.ClaimCaseRelation;
import org.springframework.stereotype.Component;

/**
 * 案件关联领域对象与数据对象之间的转换器。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Component
public class ClaimCaseRelationConverter {

    /**
     * 转换为持久化数据对象；创建与更新时间由数据库维护，不随领域对象写入。
     *
     * @param relation 案件关联
     * @return 数据对象
     */
    public ClaimCaseRelationDO toDataObject(ClaimCaseRelation relation) {
        if (relation == null) {
            return null;
        }
        return ClaimCaseRelationDO.builder()
                .tenantCode(relation.getTenantCode())
                .claimApplicationNo(relation.getClaimApplicationNo())
                .caseNo(relation.getCaseNo())
                .build();
    }
}
