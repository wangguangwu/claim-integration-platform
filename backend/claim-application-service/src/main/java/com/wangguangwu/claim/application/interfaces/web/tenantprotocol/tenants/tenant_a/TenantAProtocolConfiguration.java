package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.tenants.tenant_a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.pipeline.DefaultTenantProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.pipeline.TenantProtocolPipeline;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.stage.BeanValidationTenantProtocolValidator;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.stage.DefaultTenantProtocolDecryptor;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.stage.DefaultTenantProtocolSignatureVerifier;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 租户 A 的协议流水线注册，当前使用默认流水线实现。
 *
 * <p>新增租户时在此按租户编码与协议版本注册新的流水线实例，签收入口与注册中心不变。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Configuration(proxyBeanMethods = false)
public class TenantAProtocolConfiguration {

    private static final String TENANT_CODE = "tenant-a";
    private static final String PROTOCOL_VERSION = "v1";

    /**
     * 注册租户 A 的默认协议流水线。
     *
     * @param objectMapper 报文反序列化器
     * @param validator    Bean Validation 校验器
     * @return 租户 A 的协议流水线
     */
    @Bean
    public TenantProtocolPipeline tenantAProtocolPipeline(ObjectMapper objectMapper, Validator validator) {
        return new DefaultTenantProtocolPipeline<>(
                TENANT_CODE,
                PROTOCOL_VERSION,
                new DefaultTenantProtocolSignatureVerifier(),
                new DefaultTenantProtocolDecryptor(),
                new TenantARequestParser(objectMapper),
                new TenantARequestConverter(),
                new BeanValidationTenantProtocolValidator<>(validator));
    }
}
