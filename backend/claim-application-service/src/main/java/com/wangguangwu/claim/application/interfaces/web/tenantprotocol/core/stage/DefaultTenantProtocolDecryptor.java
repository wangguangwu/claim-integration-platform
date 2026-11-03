package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.core.stage;

/**
 * 默认租户协议解密实现，示例协议使用明文，原样返回。
 *
 * <p>接入真实租户时替换为可解密报文的实现，流水线装配方式不变。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public class DefaultTenantProtocolDecryptor implements TenantProtocolDecryptor {

    /**
     * 示例协议使用明文，默认不做转换。
     *
     * @param rawPayload 已验签的原始请求正文
     * @return 原始请求正文
     */
    @Override
    public String decrypt(String rawPayload) {
        return rawPayload;
    }
}
