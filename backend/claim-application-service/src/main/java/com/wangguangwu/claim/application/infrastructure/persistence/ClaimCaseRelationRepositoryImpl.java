package com.wangguangwu.claim.application.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wangguangwu.claim.application.domain.claimcase.ClaimCaseRelation;
import com.wangguangwu.claim.application.domain.claimcase.port.ClaimCaseRelationRepository;
import com.wangguangwu.claim.application.shared.exception.RepositoryConflictException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的案件关联仓储实现。
 *
 * <p>唯一键冲突由本实现翻译为 {@link RepositoryConflictException}，交由上层按重投语义处理。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@RequiredArgsConstructor
@Repository
public class ClaimCaseRelationRepositoryImpl implements ClaimCaseRelationRepository {

    private final ClaimCaseRelationDOMapper relationMapper;
    private final ClaimCaseRelationConverter relationConverter;

    /**
     * 查询申请已登记的全部案件号。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 已登记案件号
     */
    @Override
    public List<String> findCaseNos(String claimApplicationNo) {
        return relationMapper.selectList(Wrappers.lambdaQuery(ClaimCaseRelationDO.class)
                        .eq(ClaimCaseRelationDO::getClaimApplicationNo, claimApplicationNo))
                .stream()
                .map(ClaimCaseRelationDO::getCaseNo)
                .toList();
    }

    /**
     * 按租户与案件号反查平台申请编号。
     *
     * @param tenantCode 租户编码
     * @param caseNo     案件号
     * @return 平台申请编号
     */
    @Override
    public Optional<String> findClaimApplicationNo(String tenantCode, String caseNo) {
        return Optional.ofNullable(relationMapper.selectOne(Wrappers.lambdaQuery(ClaimCaseRelationDO.class)
                        .eq(ClaimCaseRelationDO::getTenantCode, tenantCode)
                        .eq(ClaimCaseRelationDO::getCaseNo, caseNo)))
                .map(ClaimCaseRelationDO::getClaimApplicationNo);
    }

    /**
     * 批量插入案件关联，逐条写入以便定位冲突记录。
     *
     * @param relations 待插入关联
     */
    @Override
    public void insertAll(List<ClaimCaseRelation> relations) {
        if (relations == null || relations.isEmpty()) {
            return;
        }
        try {
            for (ClaimCaseRelation relation : relations) {
                relationMapper.insert(relationConverter.toDataObject(relation));
            }
        } catch (DuplicateKeyException exception) {
            throw new RepositoryConflictException("案件关联唯一键冲突，tenantCode=" + relations.get(0).getTenantCode()
                    + "，claimApplicationNo=" + relations.get(0).getClaimApplicationNo(), exception);
        }
    }
}
