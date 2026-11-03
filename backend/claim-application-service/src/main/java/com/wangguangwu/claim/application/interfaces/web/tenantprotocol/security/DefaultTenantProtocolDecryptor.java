package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认租户协议解密实现，在真实租户加密规则接入前原样放行明文报文。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@Component
public class DefaultTenantProtocolDecryptor implements TenantProtocolDecryptor {

    /**
     * 记录解密阶段已执行并原样返回报文，不输出报文内容。
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
