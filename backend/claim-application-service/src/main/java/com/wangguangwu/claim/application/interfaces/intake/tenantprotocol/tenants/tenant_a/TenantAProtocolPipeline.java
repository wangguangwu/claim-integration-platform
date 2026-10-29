package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants.tenant_a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.pipeline.AbstractTenantProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.BeanValidationTenantProtocolValidator;
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

    public TenantAProtocolPipeline(ObjectMapper objectMapper, Validator validator) {
        super(TENANT_CODE, PROTOCOL_VERSION, new TenantARequestParser(objectMapper),
                new BeanValidationTenantProtocolValidator<>(validator), new TenantARequestConverter());
    }
}
