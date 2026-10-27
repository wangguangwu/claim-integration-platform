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

    /**
     * 绑定当前请求的租户信息，由入口过滤器在请求进入时调用。
     *
     * @param tenantInfo 租户与协议版本
     */
    public static void set(TenantInfo tenantInfo) {
        HOLDER.set(tenantInfo);
    }

    /**
     * 获取当前请求的租户信息，未绑定时返回 null。
     *
     * @return 租户信息
     */
    public static TenantInfo get() {
        return HOLDER.get();
    }

    /**
     * 获取当前请求的租户编码，供数据隔离与协议路由使用。
     *
     * @return 租户编码，未绑定时为 null
     */
    public static String tenantCode() {
        TenantInfo tenantInfo = HOLDER.get();
        return tenantInfo == null ? null : tenantInfo.tenantCode();
    }

    /**
     * 清理当前线程的租户信息，避免线程池复用造成串号。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
