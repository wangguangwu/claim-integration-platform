package com.wangguangwu.claim.foundation.tenant.context;

/**
 * 当前线程的租户上下文。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public final class TenantContext {

    private static final ThreadLocal<TenantInfo> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantInfo tenantInfo) {
        HOLDER.set(tenantInfo);
    }

    public static TenantInfo get() {
        return HOLDER.get();
    }

    public static String tenantCode() {
        TenantInfo tenantInfo = HOLDER.get();
        return tenantInfo == null ? null : tenantInfo.tenantCode();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
