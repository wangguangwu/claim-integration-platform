package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.AbstractTenantProtocolPipeline;
import org.springframework.stereotype.Component;

/**
 * B 租户协议流水线，使用默认验签、解密节点并串联解析、校验与转换组件。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class TenantBProtocolPipeline extends AbstractTenantProtocolPipeline<TenantBIntakeRequest> {

    private static final String TENANT_CODE = "tenant-b";
    private static final String PROTOCOL_VERSION = "v1";

    public TenantBProtocolPipeline(ObjectMapper objectMapper) {
        super(TENANT_CODE, PROTOCOL_VERSION, new TenantBRequestParser(objectMapper),
                new TenantBRequestValidator(), new TenantBRequestConverter());
    }
}
