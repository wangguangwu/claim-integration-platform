package com.wangguangwu.claim.application.infrastructure.query.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssueStatus;
import com.wangguangwu.claim.application.domain.claim.material.MaterialStatus;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimApplicationDO;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimApplicationDOMapper;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimApplicationIssueDO;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimApplicationIssueDOMapper;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimCaseRelationDO;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.ClaimCaseRelationDOMapper;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.MaterialDO;
import com.wangguangwu.claim.application.infrastructure.claim.persistence.MaterialDOMapper;
import com.wangguangwu.claim.application.usecase.query.model.CaseView;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationSummary;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithCases;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithIssues;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithMaterials;
import com.wangguangwu.claim.application.usecase.query.model.IssueView;
import com.wangguangwu.claim.application.usecase.query.model.MaterialView;
import com.wangguangwu.claim.application.usecase.query.port.ClaimApplicationDetailQuery;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的理赔申请详情读模型实现。
 *
 * <p>直接使用各表 Mapper 查询，不加载领域聚合、不写任何数据；申请摘要同时按平台编号与租户编码定位，
 * 租户不匹配按不存在返回空。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@RequiredArgsConstructor
@Repository
public class MyBatisClaimApplicationDetailQuery implements ClaimApplicationDetailQuery {

    /** 查询当前有效材料时使用的未删除标记。 */
    private static final boolean NOT_DELETED = false;

    /** 未关闭问题件的数据库标记。 */
    private static final int OPEN_FLAG = 1;

    private final ClaimApplicationDOMapper claimApplicationMapper;
    private final MaterialDOMapper materialMapper;
    private final ClaimApplicationIssueDOMapper issueMapper;
    private final ClaimCaseRelationDOMapper caseRelationMapper;

    /**
     * 查询申请摘要与未删除材料。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 申请摘要与材料；申请不存在或租户不匹配时为空
     */
    @Override
    public Optional<ClaimApplicationWithMaterials> findWithMaterials(String tenantCode, String claimApplicationNo) {
        return findSummary(tenantCode, claimApplicationNo)
                .map(summary -> new ClaimApplicationWithMaterials(summary,
                        findMaterials(claimApplicationNo)));
    }

    /**
     * 查询申请摘要与全部问题件。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 申请摘要与问题件；申请不存在或租户不匹配时为空
     */
    @Override
    public Optional<ClaimApplicationWithIssues> findWithIssues(String tenantCode, String claimApplicationNo) {
        return findSummary(tenantCode, claimApplicationNo)
                .map(summary -> new ClaimApplicationWithIssues(summary, findIssues(claimApplicationNo)));
    }

    /**
     * 查询申请摘要与案件关联。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 申请摘要与案件关联；申请不存在或租户不匹配时为空
     */
    @Override
    public Optional<ClaimApplicationWithCases> findWithCases(String tenantCode, String claimApplicationNo) {
        return findSummary(tenantCode, claimApplicationNo)
                .map(summary -> new ClaimApplicationWithCases(summary, findCases(claimApplicationNo)));
    }

    /**
     * 查询申请当前未关闭的问题件。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 未关闭问题件；不存在或租户不匹配时为空
     */
    @Override
    public Optional<IssueView> findOpenIssue(String tenantCode, String claimApplicationNo) {
        if (findSummary(tenantCode, claimApplicationNo).isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(issueMapper.selectOne(Wrappers.lambdaQuery(ClaimApplicationIssueDO.class)
                        .eq(ClaimApplicationIssueDO::getClaimApplicationNo, claimApplicationNo)
                        .eq(ClaimApplicationIssueDO::getIsOpen, OPEN_FLAG)))
                .map(this::toIssueView);
    }

    private Optional<ClaimApplicationSummary> findSummary(String tenantCode, String claimApplicationNo) {
        return Optional.ofNullable(claimApplicationMapper.selectOne(Wrappers.lambdaQuery(ClaimApplicationDO.class)
                        .eq(ClaimApplicationDO::getClaimApplicationNo, claimApplicationNo)
                        .eq(ClaimApplicationDO::getTenantCode, tenantCode)))
                .map(this::toSummary);
    }

    private List<MaterialView> findMaterials(String claimApplicationNo) {
        return materialMapper.selectList(Wrappers.lambdaQuery(MaterialDO.class)
                        .eq(MaterialDO::getClaimApplicationNo, claimApplicationNo)
                        .eq(MaterialDO::getDeleted, NOT_DELETED)
                        .orderByAsc(MaterialDO::getId))
                .stream()
                .map(this::toMaterialView)
                .toList();
    }

    private List<IssueView> findIssues(String claimApplicationNo) {
        return issueMapper.selectList(Wrappers.lambdaQuery(ClaimApplicationIssueDO.class)
                        .eq(ClaimApplicationIssueDO::getClaimApplicationNo, claimApplicationNo)
                        .orderByDesc(ClaimApplicationIssueDO::getCreatedAt)
                        .orderByDesc(ClaimApplicationIssueDO::getId))
                .stream()
                .map(this::toIssueView)
                .toList();
    }

    private List<CaseView> findCases(String claimApplicationNo) {
        return caseRelationMapper.selectList(Wrappers.lambdaQuery(ClaimCaseRelationDO.class)
                        .eq(ClaimCaseRelationDO::getClaimApplicationNo, claimApplicationNo)
                        .orderByAsc(ClaimCaseRelationDO::getCreatedAt)
                        .orderByAsc(ClaimCaseRelationDO::getId))
                .stream()
                .map(this::toCaseView)
                .toList();
    }

    private ClaimApplicationSummary toSummary(ClaimApplicationDO applicationDO) {
        return new ClaimApplicationSummary(applicationDO.getTenantCode(), applicationDO.getClaimApplicationNo(),
                applicationDO.getExternalClaimApplicationNo(),
                ClaimApplicationStatus.valueOf(applicationDO.getStatus()), applicationDO.getClaimApplicationAttempt(),
                applicationDO.getCreatedAt(), applicationDO.getUpdatedAt());
    }

    private MaterialView toMaterialView(MaterialDO materialDO) {
        return new MaterialView(materialDO.getBatchNo(), materialDO.getMaterialType(),
                materialDO.getOriginalFileUrl(), materialDO.getUploadedFileUrl(),
                MaterialStatus.valueOf(materialDO.getStatus()), materialDO.getCreatedAt());
    }

    private IssueView toIssueView(ClaimApplicationIssueDO issueDO) {
        return new IssueView(issueDO.getIssueNo(), issueDO.getReason(), issueDO.getAuditor(),
                issueDO.getIssueContentJson(), ClaimApplicationIssueStatus.valueOf(issueDO.getStatus()),
                issueDO.getClosedAt(), issueDO.getCreatedAt());
    }

    private CaseView toCaseView(ClaimCaseRelationDO relationDO) {
        return new CaseView(relationDO.getCaseNo(), relationDO.getCreatedAt());
    }
}
