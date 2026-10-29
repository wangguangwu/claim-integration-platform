package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.TenantProtocolParser;

/**
 * A 租户报文解析器，把请求正文反序列化为 {@link TenantAIntakeRequest}。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class TenantARequestParser implements TenantProtocolParser<TenantAIntakeRequest> {

    private final ObjectMapper objectMapper;

    public TenantARequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析请求正文，不合法 JSON 或结构不匹配时返回报文错误。
     *
     * @param rawPayload 原始请求正文
     * @return A 租户 request
     */
    @Override
    public TenantAIntakeRequest parse(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, TenantAIntakeRequest.class);
        } catch (JsonProcessingException exception) {
            throw ApplicationIntakeException.invalid("租户申请报文不是合法的 A 租户协议 JSON");
        }
    }
}
