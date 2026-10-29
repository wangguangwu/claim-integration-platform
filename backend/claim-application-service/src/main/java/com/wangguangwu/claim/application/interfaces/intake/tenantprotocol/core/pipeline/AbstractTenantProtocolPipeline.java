package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.pipeline;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeException;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.BeanValidationTenantProtocolValidator;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.TenantProtocolConverter;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.TenantProtocolParser;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.TenantProtocolValidator;
import com.wangguangwu.claim.application.usecase.intake.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import jakarta.validation.Validator;

/**
 * 租户协议流水线模板，固定「验签 → 解密 → 解析 → 校验 → 转换」执行链路。
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
    private final TenantProtocolParser<R> parser;
    private final TenantProtocolConverter<R> converter;
    private final TenantProtocolValidator<R> constraintValidator;

    /**
     * 装配流水线，校验节点使用默认的注解约束校验器。
     *
     * @param tenantCode      租户编码
     * @param protocolVersion 协议版本
     * @param parser          解析组件
     * @param converter       转换组件
     * @param validator       Bean Validation 校验器，用于校验租户 request 上声明的约束
     */
    protected AbstractTenantProtocolPipeline(String tenantCode, String protocolVersion, TenantProtocolParser<R> parser,
                                             TenantProtocolConverter<R> converter, Validator validator) {
        this.tenantCode = tenantCode;
        this.protocolVersion = protocolVersion;
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
        verifySignature(rawPayload);
        String plaintextPayload = decrypt(rawPayload);
        R request = parser.parse(plaintextPayload);
        if (request == null) {
            throw ClaimApplicationIntakeException.invalid("租户申请报文必须为 JSON 对象");
        }
        constraintValidator.validate(request);
        validateTenantRules(request);
        return converter.convert(tenantCode, request);
    }

    /**
     * 验证租户协议签名。示例协议没有签名，默认直接通过；真实租户可覆写此方法。
     *
     * @param rawPayload 原始请求正文
     */
    protected void verifySignature(String rawPayload) {
        // 示例协议不包含签名，真实租户在子类中完成验签并按统一异常体系拒绝非法报文。
    }

    /**
     * 解密租户协议正文。示例协议使用明文，默认原样返回；真实租户可覆写此方法。
     *
     * @param rawPayload 已验签的原始请求正文
     * @return 解密后的明文正文
     */
    protected String decrypt(String rawPayload) {
        return rawPayload;
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
