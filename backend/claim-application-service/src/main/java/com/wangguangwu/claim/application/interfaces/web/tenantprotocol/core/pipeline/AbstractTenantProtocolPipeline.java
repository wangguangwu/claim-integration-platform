package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.core.pipeline;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.core.stage.BeanValidationTenantProtocolValidator;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.core.stage.TenantProtocolConverter;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.core.stage.TenantProtocolDecryptor;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.core.stage.TenantProtocolParser;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.core.stage.TenantProtocolSignatureVerifier;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.core.stage.TenantProtocolValidator;
import com.wangguangwu.claim.application.shared.exception.BusinessException;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import jakarta.validation.Validator;

/**
 * 租户协议流水线模板，固定「验签 → 解密 → 解析 → 校验 → 转换」五阶段执行链路。
 *
 * <p>校验节点默认由注解约束校验器承担，租户只需在 request 上声明约束；注解无法表达的
 * 跨字段、跨节点规则通过 {@link #validateTenantRules(Object)} 扩展点追加，注解约束始终先行执行，
 * 不会被租户覆盖或关闭。转换节点最终产出签收用例的 {@link ClaimApplicationIntakeCommand}。</p>
 *
 * @param <R> 租户 request 类型
 * @author wangguangwu
 * @since 2026-10-27
 */
public abstract class AbstractTenantProtocolPipeline<R> implements TenantProtocolPipeline {

    private final String tenantCode;
    private final String protocolVersion;
    private final TenantProtocolSignatureVerifier signatureVerifier;
    private final TenantProtocolDecryptor decryptor;
    private final TenantProtocolParser<R> parser;
    private final TenantProtocolConverter<R> converter;
    private final TenantProtocolValidator<R> constraintValidator;

    /**
     * 装配五阶段流水线，校验节点使用默认的注解约束校验器。
     *
     * @param tenantCode        租户编码
     * @param protocolVersion   协议版本
     * @param signatureVerifier 验签组件
     * @param decryptor         解密组件
     * @param parser            解析组件
     * @param converter         转换组件
     * @param validator         Bean Validation 校验器，用于校验租户 request 上声明的约束
     */
    protected AbstractTenantProtocolPipeline(String tenantCode, String protocolVersion,
                                             TenantProtocolSignatureVerifier signatureVerifier,
                                             TenantProtocolDecryptor decryptor, TenantProtocolParser<R> parser,
                                             TenantProtocolConverter<R> converter, Validator validator) {
        this.tenantCode = tenantCode;
        this.protocolVersion = protocolVersion;
        this.signatureVerifier = signatureVerifier;
        this.decryptor = decryptor;
        this.parser = parser;
        this.converter = converter;
        this.constraintValidator = new BeanValidationTenantProtocolValidator<>(validator);
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
     * 依次执行验签、解密、解析、注解约束校验、租户扩展校验与转换。
     *
     * @param rawPayload 原始请求正文
     * @return 签收用例的标准命令
     */
    @Override
    public final ClaimApplicationIntakeCommand execute(String rawPayload) {
        signatureVerifier.verify(rawPayload);
        String plaintextPayload = decryptor.decrypt(rawPayload);
        R request = parser.parse(plaintextPayload);
        if (request == null) {
            throw new BusinessException(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_INVALID,
                    "租户申请报文必须为 JSON 对象");
        }
        constraintValidator.validate(request);
        validateTenantRules(request);
        return converter.convert(tenantCode, request);
    }

    /**
     * 租户私有校验扩展点，用于注解无法表达的跨字段、跨节点规则。
     *
     * <p>默认不追加规则。租户可覆写本方法，或在此委托一个实现 {@link TenantProtocolValidator}
     * 的规则类；入参已通过注解约束校验，违规时按统一业务异常拒绝。</p>
     *
     * @param request 已通过注解约束校验的租户 request
     */
    protected void validateTenantRules(R request) {
        // 默认无租户私有规则，真实租户按协议补充。
    }
}
