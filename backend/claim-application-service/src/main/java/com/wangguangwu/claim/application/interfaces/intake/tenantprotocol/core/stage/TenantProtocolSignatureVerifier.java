package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage;

/**
 * 租户协议验签阶段。
 *
 * <p>验签失败必须抛出统一业务异常并以签收请求非法码对外返回，不得让原始异常穿透到调用方。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public interface TenantProtocolSignatureVerifier {

    /**
     * 验证原始报文签名。
     *
     * @param rawPayload 原始请求正文
     */
    void verify(String rawPayload);
}
