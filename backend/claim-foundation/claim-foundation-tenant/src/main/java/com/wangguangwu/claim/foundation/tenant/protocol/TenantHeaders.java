package com.wangguangwu.claim.foundation.tenant.protocol;

/**
 * 租户相关请求头。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public final class TenantHeaders {

    /**
     * 租户编码。
     */
    public static final String TENANT_CODE = "X-Tenant-Code";

    /**
     * 协议版本。
     */
    public static final String PROTOCOL_VERSION = "X-Protocol-Version";

    private TenantHeaders() {
    }
}
