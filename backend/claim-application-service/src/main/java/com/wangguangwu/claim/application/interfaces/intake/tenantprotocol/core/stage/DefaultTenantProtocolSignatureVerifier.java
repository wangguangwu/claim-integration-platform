package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage;

/**
 * 默认租户协议验签实现，示例协议不包含签名，直接通过。
 *
 * <p>接入真实租户时替换为可校验签名与时间戳的实现，流水线装配方式不变。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public class DefaultTenantProtocolSignatureVerifier implements TenantProtocolSignatureVerifier {

    /**
     * 示例协议没有签名，默认不执行校验。
     *
     * @param rawPayload 原始请求正文
     */
    @Override
    public void verify(String rawPayload) {
        // 示例协议无签名，真实租户替换本实现。
    }
}
