package com.wangguangwu.claim.application.domain.claim;

/**
 * 查询租户外部申请是否已签收的端口。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface ApplicationExistenceQuery {

    /**
     * 按租户与外部申请编号查询理赔申请是否存在。
     *
     * @param tenantCode            租户编码
     * @param externalApplicationNo 外部申请编号
     * @return 存在时为 true
     */
    boolean exists(String tenantCode, String externalApplicationNo);

    /**
     * 删除申请存在缓存，使下一次读取回源 MySQL。
     *
     * @param tenantCode            租户编码
     * @param externalApplicationNo 外部申请编号
     */
    void evict(String tenantCode, String externalApplicationNo);
}
