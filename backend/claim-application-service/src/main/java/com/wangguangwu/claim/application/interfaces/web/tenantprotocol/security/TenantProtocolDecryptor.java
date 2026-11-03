package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security;

/**
 * 租户协议解密端口，隔离当前明文放行与后续真实解密算法。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public interface TenantProtocolDecryptor {

    /**
     * 解密已验签的租户报文。
     *
     * @param rawPayload 已验签的原始请求正文
     * @return 可供标准报文转换的明文正文
     */
    String decrypt(String rawPayload);
}
