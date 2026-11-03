package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security;

/**
 * 租户协议验签端口，隔离当前空实现与后续真实签名算法。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public interface TenantProtocolSignatureVerifier {

    /**
     * 校验租户原始报文的签名。
     *
     * @param rawPayload 租户原始请求正文
     */
    void verify(String rawPayload);
}
