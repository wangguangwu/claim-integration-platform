package com.wangguangwu.claim.application.domain.application;

import java.util.Optional;

/**
 * Application 聚合的持久化端口。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface ApplicationRepository {

    /**
     * 按租户和外部申请号查询申请。
     *
     * @param tenantCode 租户编码
     * @param externalApplicationNo 外部申请编号
     * @return 匹配的 Application
     */
    Optional<Application> findByTenantAndExternalApplicationNo(String tenantCode, String externalApplicationNo);

    /**
     * 判断同租户外部申请号是否已存在，供存在性缓存在未命中时回源。
     *
     * @param tenantCode 租户编码
     * @param externalApplicationNo 外部申请编号
     * @return 存在时为 true
     */
    boolean exists(String tenantCode, String externalApplicationNo);

    /**
     * 查询指定编号前缀下已持久化的最大申请编号。
     *
     * <p>仅在 Redis 计数器缺失或不可用时使用；调用方负责递增尾号和处理唯一约束冲突。</p>
     *
     * @param numberPrefix Application 编号前缀，例如 {@code A20261027}
     * @return 指定前缀下的最大编号；当天尚无申请时为空
     */
    Optional<String> findMaxApplicationNoByPrefix(String numberPrefix);

    /**
     * 新增 Application 聚合。
     *
     * @param application 待持久化的聚合
     */
    void insert(Application application);

    /**
     * 仅在当前状态仍为受理失败时写入重新申请结果。
     *
     * @param application 已执行重新申请状态转换的聚合
     * @return 更新成功时为 true
     */
    boolean updateForReapply(Application application);
}
