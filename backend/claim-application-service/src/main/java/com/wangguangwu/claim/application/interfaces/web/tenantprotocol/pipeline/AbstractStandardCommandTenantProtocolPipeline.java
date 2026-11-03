package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.pipeline;

import com.wangguangwu.claim.application.application.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security.TenantProtocolDecryptor;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.security.TenantProtocolSignatureVerifier;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.standard.StandardClaimApplicationIntakeCommandConverter;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 使用平台标准报文的租户协议流水线基类。
 *
 * <p>在真实协议接入前统一完成空验签、明文解密与标准命令转换；租户子类只声明自身身份，
 * 后续可按租户替换为私有协议流水线而不影响注册中心和签收入口。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
public abstract class AbstractStandardCommandTenantProtocolPipeline implements TenantProtocolPipeline {

    private final String tenantName;
    private final String tenantCode;
    private final String protocolVersion;
    private final TenantProtocolSignatureVerifier signatureVerifier;
    private final TenantProtocolDecryptor decryptor;
    private final StandardClaimApplicationIntakeCommandConverter commandConverter;

    /**
     * 装配租户标准命令流水线。
     *
     * @param tenantName 租户中文名称
     * @param tenantCode 租户编码
     * @param protocolVersion 协议版本
     * @param signatureVerifier 验签组件
     * @param decryptor 解密组件
     * @param commandConverter 标准命令转换器
     */
    protected AbstractStandardCommandTenantProtocolPipeline(String tenantName, String tenantCode, String protocolVersion,
                                                            TenantProtocolSignatureVerifier signatureVerifier,
                                                            TenantProtocolDecryptor decryptor,
                                                            StandardClaimApplicationIntakeCommandConverter commandConverter) {
        this.tenantName = tenantName;
        this.tenantCode = tenantCode;
        this.protocolVersion = protocolVersion;
        this.signatureVerifier = signatureVerifier;
        this.decryptor = decryptor;
        this.commandConverter = commandConverter;
    }

    /**
     * 判断租户上下文是否与当前流水线完全匹配。
     *
     * @param tenantInfo 租户上下文
     * @return 匹配时为 true
     */
    @Override
    public final boolean supports(TenantInfo tenantInfo) {
        return tenantInfo != null && tenantCode.equals(tenantInfo.tenantCode())
                && protocolVersion.equals(tenantInfo.protocolVersion());
    }

    /**
     * 执行默认安全阶段，并将标准报文转换为标准受理命令。
     *
     * @param rawPayload 租户原始请求正文
     * @return 标准受理命令
     */
    @Override
    public final ClaimApplicationIntakeCommand execute(String rawPayload) {
        log.info("{}租户协议流水线开始处理标准报文，tenantCode={}，protocolVersion={}", tenantName, tenantCode,
                protocolVersion);
        signatureVerifier.verify(rawPayload);
        String plaintextPayload = decryptor.decrypt(rawPayload);
        return commandConverter.convert(tenantCode, plaintextPayload);
    }
}
