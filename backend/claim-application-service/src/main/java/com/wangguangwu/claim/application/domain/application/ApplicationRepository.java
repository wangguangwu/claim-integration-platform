package com.wangguangwu.claim.application.domain.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Application 聚合的持久化端口。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface ApplicationRepository {

    /**
     * 按租户与外部申请号查询申请，唯一键保证最多一行。
     *
     * @param tenantCode            租户编码
     * @param externalApplicationNo 外部申请编号
     * @return Application 聚合
     */
    Optional<Application> findByTenantAndExternalApplicationNo(String tenantCode, String externalApplicationNo);

    /**
     * 按平台申请编号查询申请。
     *
     * @param applicationNo 平台申请编号
     * @return Application 聚合
     */
    Optional<Application> findByApplicationNo(String applicationNo);

    /**
     * 判断同租户外部申请号是否已落库。
     *
     * @param tenantCode            租户编码
     * @param externalApplicationNo 外部申请编号
     * @return 存在时为 true
     */
    boolean exists(String tenantCode, String externalApplicationNo);

    /**
     * 按编号前缀倒序取第一条，得到当天已生成的最大申请编号。
     *
     * @param numberPrefix Application 编号前缀
     * @return 最大申请编号；前缀下无数据时为空
     */
    Optional<String> findMaxApplicationNoByPrefix(String numberPrefix);

    /**
     * 查询指定状态且在给定时间之前未再更新的申请，用于补偿滞留记录。
     *
     * @param status       目标状态
     * @param updatedBefore 更新时间上限
     * @param limit        最大条数
     * @return 待补偿申请
     */
    List<Application> findByStatusUpdatedBefore(ApplicationStatus status, LocalDateTime updatedBefore, int limit);

    /**
     * 插入新 Application 聚合。
     *
     * @param application 待插入聚合
     */
    void insert(Application application);

    /**
     * 以受理失败状态为更新条件写入重新申请结果，状态被并发改写时不覆盖。
     *
     * @param application 已变更的 Application 聚合
     * @return 更新成功时为 true
     */
    boolean updateForReapply(Application application);

    /**
     * 以期望状态和申请尝试次数为条件推进生命周期状态。
     *
     * @param application    已变更的 Application 聚合，携带目标状态
     * @param expectedStatus 更新前的状态
     * @return 更新成功时为 true；false 表示已被并发推进或尝试次数已变化
     */
    boolean updateStatus(Application application, ApplicationStatus expectedStatus);
}
