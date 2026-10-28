package com.wangguangwu.claim.application.interfaces.adapter.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.interfaces.adapter.AbstractTenantApplicationPipeline;
import org.springframework.stereotype.Component;

/**
 * Demo Insurer A 协议流水线，串联解析、校验与转换组件。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class DemoInsurerAApplicationPipeline extends AbstractTenantApplicationPipeline<DemoInsurerAIntakeRequest> {

    private static final String TENANT_CODE = "demo-insurer-a";
    private static final String PROTOCOL_VERSION = "v1";

    public DemoInsurerAApplicationPipeline(ObjectMapper objectMapper) {
        super(TENANT_CODE, PROTOCOL_VERSION, new DemoInsurerARequestParser(objectMapper),
                new DemoInsurerARequestValidator(), new DemoInsurerARequestConverter());
    }
}
