package com.wangguangwu.claim.application.interfaces.adapter;

import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeException;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;

/**
 * 租户协议流水线模板，固定“解析 → 校验 → 转换”的执行顺序。
 *
 * <p>租户新增协议只需提供三个组件并按租户编码、协议版本注册子类。</p>
 *
 * @param <R> 租户 request 类型
 * @author wangguangwu
 * @since 2026-10-27
 */
public abstract class AbstractTenantApplicationPipeline<R> implements TenantApplicationPipeline {

    private final String tenantCode;
    private final String protocolVersion;
    private final TenantApplicationParser<R> parser;
    private final TenantApplicationValidator<R> validator;
    private final TenantApplicationConverter<R> converter;

    protected AbstractTenantApplicationPipeline(String tenantCode, String protocolVersion,
                                                TenantApplicationParser<R> parser,
                                                TenantApplicationValidator<R> validator,
                                                TenantApplicationConverter<R> converter) {
        this.tenantCode = tenantCode;
        this.protocolVersion = protocolVersion;
        this.parser = parser;
        this.validator = validator;
        this.converter = converter;
    }

    /**
     * 判断租户上下文是否与当前协议完全匹配。
     *
     * @param tenantInfo 租户上下文
     * @return 匹配时为 true
     */
    @Override
    public boolean supports(TenantInfo tenantInfo) {
        return tenantInfo != null && tenantCode.equals(tenantInfo.tenantCode())
                && protocolVersion.equals(tenantInfo.protocolVersion());
    }

    /**
     * 依次执行解析、校验与转换。
     *
     * @param rawPayload 原始请求正文
     * @return 标准化签收命令
     */
    @Override
    public ApplicationIntakeCommand execute(String rawPayload) {
        R request = parser.parse(rawPayload);
        if (request == null) {
            throw TenantApplicationValidation.invalid("租户申请报文必须为 JSON 对象");
        }
        validator.validate(request);
        return converter.convert(tenantCode, request);
    }
}
