package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.stage;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认租户协议解密实现，当前租户协议使用明文。
 *
 * <p>执行时记录一条 INFO 日志说明解密阶段已执行，随后原样返回，不记录报文内容与密钥。
 * 接入真实租户时替换为可解密报文的实现，流水线装配方式不变。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Slf4j
public class DefaultTenantProtocolDecryptor implements TenantProtocolDecryptor {

    /**
     * 记录解密阶段执行并原样返回。
     *
     * @param rawPayload 已验签的原始请求正文
     * @return 原始请求正文
     */
    @Override
    public String decrypt(String rawPayload) {
        log.info("租户协议解密完成（默认实现，明文放行）");
        return rawPayload;
    }
}
