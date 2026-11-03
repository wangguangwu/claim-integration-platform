package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.tenants.pingan;

import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.pipeline.AbstractStandardCommandTenantProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security.TenantProtocolDecryptor;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security.TenantProtocolSignatureVerifier;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.standard.StandardClaimApplicationIntakeCommandConverter;
import org.springframework.stereotype.Component;

/**
 * 平安保险租户协议流水线，真实协议接入前使用平台标准报文。
 *
 * <p>当前复用默认验签、明文解密与标准命令转换；接入真实协议时在本类替换对应阶段。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Component
public class PinganProtocolPipeline extends AbstractStandardCommandTenantProtocolPipeline {

    private static final String TENANT_CODE = "PINGAN";
    private static final String PROTOCOL_VERSION = "v1";
    private static final String TENANT_NAME = "平安保险";

    /**
     * 装配平安保险标准命令流水线。
     *
     * @param signatureVerifier 默认验签组件
     * @param decryptor 默认解密组件
     * @param commandConverter 标准命令转换器
     */
    public PinganProtocolPipeline(TenantProtocolSignatureVerifier signatureVerifier, TenantProtocolDecryptor decryptor,
                                  StandardClaimApplicationIntakeCommandConverter commandConverter) {
        super(TENANT_NAME, TENANT_CODE, PROTOCOL_VERSION, signatureVerifier, decryptor, commandConverter);
    }
}
