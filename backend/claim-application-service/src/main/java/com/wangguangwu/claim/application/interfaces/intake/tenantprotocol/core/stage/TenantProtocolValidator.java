package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage;

/**
 * 租户私有报文校验器，校验必填字段与嵌套结构。
 *
 * @param <R> 租户 request 类型
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface TenantProtocolValidator<R> {

    /**
     * 校验租户 request，不合法时抛出 {@code APPLICATION_INTAKE_INVALID}。
     *
     * @param request 已解析的租户 request
     */
    void validate(R request);
}
