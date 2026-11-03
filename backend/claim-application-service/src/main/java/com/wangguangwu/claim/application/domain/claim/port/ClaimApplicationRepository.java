package com.wangguangwu.claim.application.domain.claim.port;

import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 理赔申请聚合的持久化端口。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface ClaimApplicationRepository {

    /**
     * 按租户与外部申请号查询申请，唯一键保证最多一行。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @return 理赔申请聚合
     */
    Optional<ClaimApplication> findByTenantAndExternalClaimApplicationNo(String tenantCode, String externalClaimApplicationNo);

    /**
     * 按平台申请编号查询申请。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 理赔申请聚合
     */
    Optional<ClaimApplication> findByClaimApplicationNo(String claimApplicationNo);

    /**
     * 按问题件编号查询其所属申请的完整聚合。
     *
     * @param issueNo 问题件编号
     * @return 理赔申请聚合；问题件不存在或所属申请缺失时为空
     */
    Optional<ClaimApplication> findByIssueNo(String issueNo);

    /**
     * 按编号前缀倒序取第一条，得到当天已生成的最大申请编号。
     *
     * @param numberPrefix 理赔申请编号前缀
     * @return 最大申请编号；前缀下无数据时为空
     */
    Optional<String> findMaxClaimApplicationNoByPrefix(String numberPrefix);

    /**
     * 查询指定状态且在给定时间之前未再更新的申请，用于补偿滞留记录。
     *
     * @param status       目标状态
     * @param updatedBefore 更新时间上限
     * @param limit        最大条数
     * @return 待补偿申请
     */
    List<ClaimApplication> findByStatusUpdatedBefore(ClaimApplicationStatus status, LocalDateTime updatedBefore, int limit);

    /**
     * 插入新理赔申请聚合，一次写入根与全部材料，版本由仓储初始化为 0。
     *
     * @param claimApplication 待插入聚合
     */
    void insert(ClaimApplication claimApplication);

    /**
     * 以乐观锁版本为条件保存聚合变更，成功后才写入材料与问题件。
     *
     * @param claimApplication 已变更的聚合
     * @return 保存成功时为 true；false 表示版本已过期或聚合尚未持久化
     */
    boolean save(ClaimApplication claimApplication);
}
