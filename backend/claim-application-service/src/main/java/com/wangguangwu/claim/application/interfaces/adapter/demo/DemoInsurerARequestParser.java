package com.wangguangwu.claim.application.interfaces.adapter.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationParser;
import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationValidation;

/**
 * A 租户报文解析器，把请求正文反序列化为 {@link DemoInsurerAIntakeRequest}。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class DemoInsurerARequestParser implements TenantApplicationParser<DemoInsurerAIntakeRequest> {

    private final ObjectMapper objectMapper;

    public DemoInsurerARequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析请求正文，不合法 JSON 或结构不匹配时返回报文错误。
     *
     * @param rawPayload 原始请求正文
     * @return A 租户 request
     */
    @Override
    public DemoInsurerAIntakeRequest parse(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, DemoInsurerAIntakeRequest.class);
        } catch (JsonProcessingException exception) {
            throw TenantApplicationValidation.invalid("租户申请报文不是合法的 A 租户协议 JSON");
        }
    }
}
