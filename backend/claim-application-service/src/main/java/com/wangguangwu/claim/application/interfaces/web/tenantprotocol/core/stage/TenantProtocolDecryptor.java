package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.core.stage;

/**
 * 租户协议解密阶段。
 *
 * <p>解密失败必须抛出统一业务异常并以签收请求非法码对外返回，不得把密文继续交给解析节点。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public interface TenantProtocolDecryptor {

    /**
     * 解密已验签的原始报文。
     *
     * @param rawPayload 已验签的原始请求正文
     * @return 解密后的明文正文
     */
    String decrypt(String rawPayload);
}
