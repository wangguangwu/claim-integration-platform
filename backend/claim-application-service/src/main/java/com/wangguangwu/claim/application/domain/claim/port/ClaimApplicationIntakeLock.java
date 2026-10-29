package com.wangguangwu.claim.application.domain.claim.port;

import java.util.function.Supplier;

/**
 * 同一外部申请并发签收锁。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface ClaimApplicationIntakeLock {

    /**
     * 在同一租户外部申请号的排他区间执行操作。
     *
     * @param tenantCode            租户编码
     * @param externalClaimApplicationNo 外部申请编号
     * @param action                受锁保护的操作
     * @param <T>                   返回类型
     * @return 操作结果
     */
    <T> T execute(String tenantCode, String externalClaimApplicationNo, Supplier<T> action);
}
