package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage;

/**
 * 租户私有报文解析器，把原始请求正文反序列化为租户 request 对象。
 *
 * @param <R> 租户 request 类型
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface TenantProtocolParser<R> {

    /**
     * 解析原始请求正文。
     *
     * @param rawPayload 原始请求正文
     * @return 租户 request 对象
     */
    R parse(String rawPayload);
}
