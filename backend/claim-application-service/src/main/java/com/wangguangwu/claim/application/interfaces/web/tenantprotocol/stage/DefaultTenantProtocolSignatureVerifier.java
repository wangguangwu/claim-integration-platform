package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.stage;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认租户协议验签实现，当前租户协议未启用签名校验。
 *
 * <p>执行时记录一条 INFO 日志说明验签阶段已执行，随后原样放行，不记录报文内容与签名。
 * 接入真实租户时替换为可校验签名与时间戳的实现，流水线装配方式不变。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Slf4j
public class DefaultTenantProtocolSignatureVerifier implements TenantProtocolSignatureVerifier {

    /**
     * 记录验签阶段执行并放行。
     *
     * @param rawPayload 原始请求正文
     */
    @Override
    public void verify(String rawPayload) {
        log.info("租户协议验签通过（默认实现，不校验签名）");
    }
}
