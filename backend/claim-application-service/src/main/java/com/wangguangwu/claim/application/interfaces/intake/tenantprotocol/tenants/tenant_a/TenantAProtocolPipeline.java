package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants.tenant_a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.pipeline.AbstractTenantProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.DefaultTenantProtocolDecryptor;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.DefaultTenantProtocolSignatureVerifier;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * A 租户协议流水线，使用默认验签、解密节点并串联解析、校验与转换组件。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class TenantAProtocolPipeline extends AbstractTenantProtocolPipeline<TenantAIntakeRequest> {

    private static final String TENANT_CODE = "tenant-a";
    private static final String PROTOCOL_VERSION = "v1";

    /**
     * 装配 A 租户 v1 协议的五阶段组件；验签与解密使用默认直通实现。
     *
     * @param objectMapper 租户报文反序列化器
     * @param validator    Bean Validation 校验器
     */
    public TenantAProtocolPipeline(ObjectMapper objectMapper, Validator validator) {
        super(TENANT_CODE, PROTOCOL_VERSION,
                new DefaultTenantProtocolSignatureVerifier(), new DefaultTenantProtocolDecryptor(),
                new TenantARequestParser(objectMapper), new TenantARequestConverter(), validator);
    }
}
