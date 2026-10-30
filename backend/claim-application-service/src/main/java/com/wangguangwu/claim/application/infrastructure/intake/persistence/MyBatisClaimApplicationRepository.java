package com.wangguangwu.claim.application.infrastructure.intake.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的理赔申请聚合仓储实现。
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

    private final ClaimApplicationDOMapper claimApplicationMapper;
    private final ClaimApplicationDataConverter claimApplicationDataConverter;

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
                .map(claimApplicationDataConverter::toDomain);
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
                .map(claimApplicationDataConverter::toDomain);
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
                .map(claimApplicationDataConverter::toDomain)
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
     * 插入新理赔申请聚合，并回填数据库生成主键。
     *
     * @param claimApplication 待插入聚合
     */
    @Override
    public void insert(ClaimApplication claimApplication) {
        ClaimApplicationDO claimApplicationDO = claimApplicationDataConverter.toDataObject(claimApplication);
        claimApplicationMapper.insert(claimApplicationDO);
        claimApplication.bindId(claimApplicationDO.getId());
    }

    /**
     * 以受理失败状态为更新条件写入重新申请结果，状态被并发改写时不覆盖。
     *
     * @param claimApplication 已变更的理赔申请聚合
     * @return 更新成功时为 true
     */
    @Override
    public boolean updateForReapply(ClaimApplication claimApplication) {
        ClaimApplicationDO claimApplicationDO = claimApplicationDataConverter.toDataObject(claimApplication);
        return claimApplicationMapper.update(null, Wrappers.lambdaUpdate(ClaimApplicationDO.class)
                .eq(ClaimApplicationDO::getId, claimApplicationDO.getId())
                .eq(ClaimApplicationDO::getStatus, ClaimApplicationStatus.ACCEPT_FAILED.name())
                .set(ClaimApplicationDO::getStatus, claimApplicationDO.getStatus())
                .set(ClaimApplicationDO::getClaimApplicationAttempt, claimApplicationDO.getClaimApplicationAttempt())
                .set(ClaimApplicationDO::getApplicantJson, claimApplicationDO.getApplicantJson())
                .set(ClaimApplicationDO::getPrimaryInsuredJson, claimApplicationDO.getPrimaryInsuredJson())
                .set(ClaimApplicationDO::getAccidentPersonJson, claimApplicationDO.getAccidentPersonJson())
                .set(ClaimApplicationDO::getPayeeJson, claimApplicationDO.getPayeeJson())) == 1;
    }

    /**
     * 以期望状态和申请尝试次数为条件推进状态，避免并发的迟到处理覆盖新尝试。
     *
     * @param claimApplication    已变更的理赔申请聚合
     * @param expectedStatus 更新前的状态
     * @return 更新成功时为 true
     */
    @Override
    public boolean updateStatus(ClaimApplication claimApplication, ClaimApplicationStatus expectedStatus) {
        return claimApplicationMapper.update(null, Wrappers.lambdaUpdate(ClaimApplicationDO.class)
                .eq(ClaimApplicationDO::getClaimApplicationNo, claimApplication.getClaimApplicationNo())
                .eq(ClaimApplicationDO::getStatus, expectedStatus.name())
                .eq(ClaimApplicationDO::getClaimApplicationAttempt, claimApplication.getClaimApplicationAttempt())
                .set(ClaimApplicationDO::getStatus, claimApplication.getStatus().name())) == 1;
    }
}
