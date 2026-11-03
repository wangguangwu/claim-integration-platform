package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.pipeline;

import com.wangguangwu.claim.application.application.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.stage.TenantProtocolConverter;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.stage.TenantProtocolDecryptor;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.stage.TenantProtocolParser;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.stage.TenantProtocolSignatureVerifier;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.stage.TenantProtocolValidator;
import com.wangguangwu.claim.application.shared.exception.BusinessException;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认租户协议流水线，供每个租户各注册一个实例使用。
 *
 * <p>绑定租户编码与协议版本，固定按「验签 → 解密 → 解析 → 校验 → 转换」执行；
 * 处理开始时记录租户编码与协议版本，便于在共用签收入口下区分租户。
 * 当前各租户均使用默认实现，接入真实协议差异时替换对应阶段组件即可。</p>
 *
 * @param <R> 租户 request 类型
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
public class DefaultTenantProtocolPipeline<R> implements TenantProtocolPipeline {

    private final String tenantCode;
    private final String protocolVersion;
    private final TenantProtocolSignatureVerifier signatureVerifier;
    private final TenantProtocolDecryptor decryptor;
    private final TenantProtocolParser<R> parser;
    private final TenantProtocolConverter<R> converter;
    private final TenantProtocolValidator<R> validator;

    /**
     * 装配租户协议流水线。
     *
     * @param tenantCode        租户编码
     * @param protocolVersion   协议版本
     * @param signatureVerifier 验签组件
     * @param decryptor         解密组件
     * @param parser            解析组件
     * @param converter         转换组件
     * @param validator         校验组件
     */
    public DefaultTenantProtocolPipeline(String tenantCode, String protocolVersion,
                                         TenantProtocolSignatureVerifier signatureVerifier,
                                         TenantProtocolDecryptor decryptor, TenantProtocolParser<R> parser,
                                         TenantProtocolConverter<R> converter, TenantProtocolValidator<R> validator) {
        this.tenantCode = tenantCode;
        this.protocolVersion = protocolVersion;
        this.signatureVerifier = signatureVerifier;
        this.decryptor = decryptor;
        this.parser = parser;
        this.converter = converter;
        this.validator = validator;
    }

    /**
     * 判断租户上下文是否与当前流水线完全匹配。
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
     * 依次执行验签、解密、解析、校验与转换。
     *
     * @param rawPayload 原始请求正文
     * @return 签收用例的标准命令
     */
    @Override
    public ClaimApplicationIntakeCommand execute(String rawPayload) {
        log.info("开始处理租户协议报文，tenantCode={}，protocolVersion={}", tenantCode, protocolVersion);
        signatureVerifier.verify(rawPayload);
        String plaintextPayload = decryptor.decrypt(rawPayload);
        R request = parser.parse(plaintextPayload);
        if (request == null) {
            throw new BusinessException("租户申请报文必须为 JSON 对象");
        }
        validator.validate(request);
        return converter.convert(tenantCode, request);
    }
}
