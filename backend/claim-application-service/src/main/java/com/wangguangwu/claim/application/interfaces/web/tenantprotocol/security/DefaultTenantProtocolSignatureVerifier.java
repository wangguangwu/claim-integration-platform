package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认租户协议验签实现，在真实租户签名规则接入前记录阶段日志并放行。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@Component
public class DefaultTenantProtocolSignatureVerifier implements TenantProtocolSignatureVerifier {

    /**
     * 记录验签阶段已执行，不读取或输出报文内容。
     *
     * @param rawPayload 租户原始请求正文
     */
    @Override
    public void verify(String rawPayload) {
        log.info("租户协议验签通过（默认实现，不校验签名）");
    }
}
