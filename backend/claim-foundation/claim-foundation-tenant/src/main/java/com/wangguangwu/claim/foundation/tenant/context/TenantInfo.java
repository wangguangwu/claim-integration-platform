package com.wangguangwu.claim.foundation.tenant.context;

/**
 * 租户身份与协议版本。
 *
 * @param tenantCode      租户编码
 * @param protocolVersion 协议版本
 * @author wangguangwu
 * @since 2026-10-27
 */
public record TenantInfo(String tenantCode, String protocolVersion) {
}
