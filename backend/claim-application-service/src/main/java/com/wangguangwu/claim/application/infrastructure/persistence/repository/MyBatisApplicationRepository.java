package com.wangguangwu.claim.application.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wangguangwu.claim.application.domain.application.Application;
import com.wangguangwu.claim.application.domain.application.ApplicationRepository;
import com.wangguangwu.claim.application.domain.application.ApplicationStatus;
import com.wangguangwu.claim.application.infrastructure.persistence.convert.ApplicationDataMapper;
import com.wangguangwu.claim.application.infrastructure.persistence.dataobject.ApplicationDO;
import com.wangguangwu.claim.application.infrastructure.persistence.mapper.ApplicationDOMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的 Application 聚合仓储实现。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Repository
public class MyBatisApplicationRepository implements ApplicationRepository {

    /**
     * 取最大编号只需一行，禁用分页计数以省去 count 查询。
     */
    private static final long MAX_NUMBER_PAGE_SIZE = 1L;

    private final ApplicationDOMapper applicationMapper;
    private final ApplicationDataMapper applicationDataMapper;

    public MyBatisApplicationRepository(ApplicationDOMapper applicationMapper,
                                        ApplicationDataMapper applicationDataMapper) {
        this.applicationMapper = applicationMapper;
        this.applicationDataMapper = applicationDataMapper;
    }

    /**
     * 按租户与外部申请号查询 Application，唯一键保证最多一行。
     *
     * @param tenantCode 租户编码
     * @param externalApplicationNo 外部申请编号
     * @return Application 聚合
     */
    @Override
    public Optional<Application> findByTenantAndExternalApplicationNo(String tenantCode,
                                                                     String externalApplicationNo) {
        return Optional.ofNullable(applicationMapper.selectOne(Wrappers.lambdaQuery(ApplicationDO.class)
                        .eq(ApplicationDO::getTenantCode, tenantCode)
                        .eq(ApplicationDO::getExternalApplicationNo, externalApplicationNo)))
                .map(applicationDataMapper::toDomain);
    }

    /**
     * 判断同租户外部申请号是否已落库。
     *
     * @param tenantCode 租户编码
     * @param externalApplicationNo 外部申请编号
     * @return 存在时为 true
     */
    @Override
    public boolean exists(String tenantCode, String externalApplicationNo) {
        return applicationMapper.exists(Wrappers.lambdaQuery(ApplicationDO.class)
                .eq(ApplicationDO::getTenantCode, tenantCode)
                .eq(ApplicationDO::getExternalApplicationNo, externalApplicationNo));
    }

    /**
     * 按平台申请编号查询 Application，编号全局唯一。
     *
     * @param applicationNo 平台申请编号
     * @return Application 聚合
     */
    @Override
    public Optional<Application> findByApplicationNo(String applicationNo) {
        return Optional.ofNullable(applicationMapper.selectOne(Wrappers.lambdaQuery(ApplicationDO.class)
                        .eq(ApplicationDO::getApplicationNo, applicationNo)))
                .map(applicationDataMapper::toDomain);
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
    public List<Application> findByStatusUpdatedBefore(ApplicationStatus status, LocalDateTime updatedBefore,
                                                       int limit) {
        Page<ApplicationDO> page = new Page<>(1L, limit, false);
        return applicationMapper.selectPage(page, Wrappers.lambdaQuery(ApplicationDO.class)
                        .eq(ApplicationDO::getStatus, status.name())
                        .le(ApplicationDO::getUpdatedAt, updatedBefore)
                        .orderByAsc(ApplicationDO::getUpdatedAt))
                .getRecords().stream()
                .map(applicationDataMapper::toDomain)
                .toList();
    }

    /**
     * 按编号前缀倒序取第一条，得到当天已生成的最大申请编号。
     *
     * @param numberPrefix Application 编号前缀
     * @return 最大申请编号；前缀下无数据时为空
     */
    @Override
    public Optional<String> findMaxApplicationNoByPrefix(String numberPrefix) {
        Page<ApplicationDO> page = new Page<>(1L, MAX_NUMBER_PAGE_SIZE, false);
        return applicationMapper.selectPage(page, Wrappers.lambdaQuery(ApplicationDO.class)
                        .select(ApplicationDO::getApplicationNo)
                        .likeRight(ApplicationDO::getApplicationNo, numberPrefix)
                        .orderByDesc(ApplicationDO::getApplicationNo))
                .getRecords().stream()
                .findFirst()
                .map(ApplicationDO::getApplicationNo);
    }

    /**
     * 插入新 Application 聚合，并回填数据库生成主键。
     *
     * @param application 待插入聚合
     */
    @Override
    public void insert(Application application) {
        ApplicationDO applicationDO = applicationDataMapper.toDataObject(application);
        applicationMapper.insert(applicationDO);
        application.setId(applicationDO.getId());
    }

    /**
     * 以受理失败状态为更新条件写入重新申请结果，状态被并发改写时不覆盖。
     *
     * @param application 已变更的 Application 聚合
     * @return 更新成功时为 true
     */
    @Override
    public boolean updateForReapply(Application application) {
        ApplicationDO applicationDO = applicationDataMapper.toDataObject(application);
        return applicationMapper.update(null, Wrappers.lambdaUpdate(ApplicationDO.class)
                .eq(ApplicationDO::getId, applicationDO.getId())
                .eq(ApplicationDO::getStatus, ApplicationStatus.ACCEPT_FAILED.name())
                .set(ApplicationDO::getStatus, applicationDO.getStatus())
                .set(ApplicationDO::getApplicationAttempt, applicationDO.getApplicationAttempt())
                .set(ApplicationDO::getApplicantJson, applicationDO.getApplicantJson())
                .set(ApplicationDO::getPrimaryInsuredJson, applicationDO.getPrimaryInsuredJson())
                .set(ApplicationDO::getAccidentPersonJson, applicationDO.getAccidentPersonJson())
                .set(ApplicationDO::getPayeeJson, applicationDO.getPayeeJson())) == 1;
    }

    /**
     * 以期望状态和申请尝试次数为条件推进状态，避免并发的迟到处理覆盖新尝试。
     *
     * @param application    已变更的 Application 聚合
     * @param expectedStatus 更新前的状态
     * @return 更新成功时为 true
     */
    @Override
    public boolean updateStatus(Application application, ApplicationStatus expectedStatus) {
        return applicationMapper.update(null, Wrappers.lambdaUpdate(ApplicationDO.class)
                .eq(ApplicationDO::getApplicationNo, application.getApplicationNo())
                .eq(ApplicationDO::getStatus, expectedStatus.name())
                .eq(ApplicationDO::getApplicationAttempt, application.getApplicationAttempt())
                .set(ApplicationDO::getStatus, application.getStatus().name())) == 1;
    }
}
