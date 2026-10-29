package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants.tenant_b;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.TenantProtocolParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * B 租户报文解析器，把请求正文反序列化为 {@link TenantBIntakeRequest}。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
public class TenantBRequestParser implements TenantProtocolParser<TenantBIntakeRequest> {

    private final ObjectMapper objectMapper;

    /**
     * 解析请求正文，不合法 JSON 或结构不匹配时返回报文错误。
     *
     * @param rawPayload 原始请求正文
     * @return B 租户 request
     */
    @Override
    public TenantBIntakeRequest parse(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, TenantBIntakeRequest.class);
        } catch (JsonProcessingException exception) {
            // 只记录异常类型：报文原文含证件号，不允许进日志。
            log.warn("B 租户申请报文解析失败，原因类型={}", exception.getClass().getSimpleName());
            throw ApplicationIntakeException.invalid("租户申请报文不是合法的 B 租户协议 JSON", exception);
        }
    }
}
