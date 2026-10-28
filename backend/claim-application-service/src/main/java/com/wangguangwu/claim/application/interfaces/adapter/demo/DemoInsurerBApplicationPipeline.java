package com.wangguangwu.claim.application.interfaces.adapter.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.interfaces.adapter.AbstractTenantApplicationPipeline;
import org.springframework.stereotype.Component;

/**
 * Demo Insurer B 协议流水线，串联解析、校验与转换组件。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class DemoInsurerBApplicationPipeline extends AbstractTenantApplicationPipeline<DemoInsurerBIntakeRequest> {

    private static final String TENANT_CODE = "demo-insurer-b";
    private static final String PROTOCOL_VERSION = "v1";

    public DemoInsurerBApplicationPipeline(ObjectMapper objectMapper) {
        super(TENANT_CODE, PROTOCOL_VERSION, new DemoInsurerBRequestParser(objectMapper),
                new DemoInsurerBRequestValidator(), new DemoInsurerBRequestConverter());
    }
}
