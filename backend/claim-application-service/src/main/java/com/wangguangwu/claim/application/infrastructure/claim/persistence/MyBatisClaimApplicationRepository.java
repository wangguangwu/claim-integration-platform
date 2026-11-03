package com.wangguangwu.claim.application.infrastructure.claim.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.claim.material.Material;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的理赔申请聚合仓储实现。
 *
 * <p>聚合根、当前有效材料与全部问题件始终整体加载；保存时先以版本条件更新根，
 * 版本不命中即放弃全部子表写入，避免并发请求留下部分变更。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Repository
public class MyBatisClaimApplicationRepository implements ClaimApplicationRepository {

    /**
     * 取最大编号只需一行，禁用分页计数以省去 count 查询。
     */
    private static final long MAX_NUMBER_PAGE_SIZE = 1L;

    /** 查询当前有效材料时使用的未删除标记。 */
    private static final boolean NOT_DELETED = false;

    private final ClaimApplicationDOMapper claimApplicationMapper;
    private final ClaimApplicationDataConverter claimApplicationDataConverter;
    private final MaterialDOMapper materialMapper;
    private final MaterialDataConverter materialDataConverter;
    private final ClaimApplicationIssueDOMapper issueMapper;
    private final ClaimApplicationIssueConverter issueConverter;

    /**
     * 按租户与外部申请号查询理赔申请，唯一键保证最多一行。
     *
     * @param tenantCode 租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @return 理赔申请聚合
     */
    @Override
    public Optional<ClaimApplication> findByTenantAndExternalClaimApplicationNo(String tenantCode,
                                                                     String externalClaimApplicationNo) {
        return Optional.ofNullable(claimApplicationMapper.selectOne(Wrappers.lambdaQuery(ClaimApplicationDO.class)
                        .eq(ClaimApplicationDO::getTenantCode, tenantCode)
                        .eq(ClaimApplicationDO::getExternalClaimApplicationNo, externalClaimApplicationNo)))
                .map(this::loadAggregate);
    }

    /**
     * 按平台申请编号查询理赔申请，编号全局唯一。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 理赔申请聚合
     */
    @Override
    public Optional<ClaimApplication> findByClaimApplicationNo(String claimApplicationNo) {
        return Optional.ofNullable(claimApplicationMapper.selectOne(Wrappers.lambdaQuery(ClaimApplicationDO.class)
                        .eq(ClaimApplicationDO::getClaimApplicationNo, claimApplicationNo)))
                .map(this::loadAggregate);
    }

    /**
     * 按问题件编号定位所属申请后加载完整聚合。
     *
     * @param issueNo 问题件编号
     * @return 理赔申请聚合；问题件或所属申请不存在时为空
     */
    @Override
    public Optional<ClaimApplication> findByIssueNo(String issueNo) {
        ClaimApplicationIssueDO issueDO = issueMapper.selectOne(Wrappers.lambdaQuery(ClaimApplicationIssueDO.class)
                .eq(ClaimApplicationIssueDO::getIssueNo, issueNo));
        if (issueDO == null) {
            return Optional.empty();
        }
        return findByClaimApplicationNo(issueDO.getClaimApplicationNo());
    }

    /**
     * 查询指定状态且在给定时间之前未再更新的申请，按更新时间升序返回。
     *
     * @param status        目标状态
     * @param updatedBefore 更新时间上限
     * @param limit         最大条数
     * @return 待补偿申请
     */
    @Override
    public List<ClaimApplication> findByStatusUpdatedBefore(ClaimApplicationStatus status, LocalDateTime updatedBefore,
                                                       int limit) {
        Page<ClaimApplicationDO> page = new Page<>(1L, limit, false);
        return claimApplicationMapper.selectPage(page, Wrappers.lambdaQuery(ClaimApplicationDO.class)
                        .eq(ClaimApplicationDO::getStatus, status.name())
                        .le(ClaimApplicationDO::getUpdatedAt, updatedBefore)
                        .orderByAsc(ClaimApplicationDO::getUpdatedAt))
                .getRecords().stream()
                .map(this::loadAggregate)
                .toList();
    }

    /**
     * 按编号前缀倒序取第一条，得到当天已生成的最大申请编号。
     *
     * @param numberPrefix 理赔申请编号前缀
     * @return 最大申请编号；前缀下无数据时为空
     */
    @Override
    public Optional<String> findMaxClaimApplicationNoByPrefix(String numberPrefix) {
        Page<ClaimApplicationDO> page = new Page<>(1L, MAX_NUMBER_PAGE_SIZE, false);
        return claimApplicationMapper.selectPage(page, Wrappers.lambdaQuery(ClaimApplicationDO.class)
                        .select(ClaimApplicationDO::getClaimApplicationNo)
                        .likeRight(ClaimApplicationDO::getClaimApplicationNo, numberPrefix)
                        .orderByDesc(ClaimApplicationDO::getClaimApplicationNo))
                .getRecords().stream()
                .findFirst()
                .map(ClaimApplicationDO::getClaimApplicationNo);
    }

    /**
     * 插入新理赔申请聚合：根以版本 0 写入，材料随根一次落库。
     *
     * @param claimApplication 待插入聚合
     */
    @Override
    public void insert(ClaimApplication claimApplication) {
        if (claimApplication.getId() != null) {
            throw new IllegalStateException("理赔申请已持久化，不允许重复插入，claimApplicationNo="
                    + claimApplication.getClaimApplicationNo());
        }
        ClaimApplicationDO claimApplicationDO = claimApplicationDataConverter.toDataObject(claimApplication);
        claimApplicationDO.setVersion(0L);
        claimApplicationMapper.insert(claimApplicationDO);
        claimApplication.bindId(claimApplicationDO.getId());
        claimApplication.getMaterials().forEach(material -> materialMapper.insert(
                materialDataConverter.toDataObject(claimApplication.getClaimApplicationNo(), material)));
        claimApplication.bindVersion(0L);
    }

    /**
     * 以乐观锁版本为条件保存聚合变更，版本不命中时不写任何子表。
     *
     * @param claimApplication 已变更的聚合
     * @return 保存成功时为 true；false 表示版本已过期
     */
    @Override
    public boolean save(ClaimApplication claimApplication) {
        if (claimApplication.getId() == null) {
            throw new IllegalStateException("理赔申请尚未持久化，不允许保存，claimApplicationNo="
                    + claimApplication.getClaimApplicationNo());
        }
        if (claimApplication.getVersion() == null) {
            throw new IllegalStateException("理赔申请缺少乐观锁版本，不允许保存，claimApplicationNo="
                    + claimApplication.getClaimApplicationNo());
        }
        ClaimApplicationDO claimApplicationDO = claimApplicationDataConverter.toDataObject(claimApplication);
        int updatedRows = claimApplicationMapper.update(null, Wrappers.lambdaUpdate(ClaimApplicationDO.class)
                .eq(ClaimApplicationDO::getId, claimApplicationDO.getId())
                .eq(ClaimApplicationDO::getVersion, claimApplicationDO.getVersion())
                .set(ClaimApplicationDO::getStatus, claimApplicationDO.getStatus())
                .set(ClaimApplicationDO::getClaimApplicationAttempt, claimApplicationDO.getClaimApplicationAttempt())
                .set(ClaimApplicationDO::getApplicantJson, claimApplicationDO.getApplicantJson())
                .set(ClaimApplicationDO::getPrimaryInsuredJson, claimApplicationDO.getPrimaryInsuredJson())
                .set(ClaimApplicationDO::getAccidentPersonJson, claimApplicationDO.getAccidentPersonJson())
                .set(ClaimApplicationDO::getPayeeJson, claimApplicationDO.getPayeeJson())
                .set(ClaimApplicationDO::getVersion, claimApplicationDO.getVersion() + 1));
        if (updatedRows == 0) {
            return false;
        }
        saveMaterials(claimApplication);
        saveIssues(claimApplication);
        claimApplication.bindVersion(claimApplication.getVersion() + 1);
        return true;
    }

    /**
     * 一次加载根、当前有效材料与全部问题件，保证聚合完整性。
     *
     * @param claimApplicationDO 根数据对象
     * @return 完整聚合
     */
    private ClaimApplication loadAggregate(ClaimApplicationDO claimApplicationDO) {
        String claimApplicationNo = claimApplicationDO.getClaimApplicationNo();
        List<Material> materials = materialMapper.selectList(Wrappers.lambdaQuery(MaterialDO.class)
                        .eq(MaterialDO::getClaimApplicationNo, claimApplicationNo)
                        .eq(MaterialDO::getDeleted, NOT_DELETED)
                        .orderByAsc(MaterialDO::getId))
                .stream()
                .map(materialDataConverter::toDomain)
                .toList();
        List<ClaimApplicationIssue> issues = issueMapper.selectList(Wrappers.lambdaQuery(ClaimApplicationIssueDO.class)
                        .eq(ClaimApplicationIssueDO::getClaimApplicationNo, claimApplicationNo)
                        .orderByAsc(ClaimApplicationIssueDO::getId))
                .stream()
                .map(issueConverter::toDomain)
                .toList();
        return claimApplicationDataConverter.toDomain(claimApplicationDO, materials, issues);
    }

    /**
     * 新增材料插入，已落库材料按主键更新处理状态与逻辑删除标记。
     *
     * @param claimApplication 已通过版本校验的聚合
     */
    private void saveMaterials(ClaimApplication claimApplication) {
        for (Material material : claimApplication.getMaterials()) {
            MaterialDO materialDO = materialDataConverter.toDataObject(claimApplication.getClaimApplicationNo(),
                    material);
            if (materialDO.getId() == null) {
                materialMapper.insert(materialDO);
                continue;
            }
            materialMapper.update(null, Wrappers.lambdaUpdate(MaterialDO.class)
                    .eq(MaterialDO::getId, materialDO.getId())
                    .set(MaterialDO::getStatus, materialDO.getStatus())
                    .set(MaterialDO::getUploadedFileUrl, materialDO.getUploadedFileUrl())
                    .set(MaterialDO::getDeleted, materialDO.getDeleted())
                    .set(MaterialDO::getDeletedAt, materialDO.getDeletedAt()));
        }
    }

    /**
     * 新增问题件插入，已落库问题件只更新状态、审核人与关闭时间，原因与详情不可变。
     *
     * @param claimApplication 已通过版本校验的聚合
     */
    private void saveIssues(ClaimApplication claimApplication) {
        for (ClaimApplicationIssue issue : claimApplication.getIssues()) {
            ClaimApplicationIssueDO issueDO = issueConverter.toDataObject(claimApplication.getClaimApplicationNo(),
                    issue);
            if (issueDO.getId() == null) {
                issueMapper.insert(issueDO);
                continue;
            }
            issueMapper.update(null, Wrappers.lambdaUpdate(ClaimApplicationIssueDO.class)
                    .eq(ClaimApplicationIssueDO::getId, issueDO.getId())
                    .set(ClaimApplicationIssueDO::getStatus, issueDO.getStatus())
                    .set(ClaimApplicationIssueDO::getIsOpen, issueDO.getIsOpen())
                    .set(ClaimApplicationIssueDO::getAuditor, issueDO.getAuditor())
                    .set(ClaimApplicationIssueDO::getClosedAt, issueDO.getClosedAt()));
        }
    }
}
