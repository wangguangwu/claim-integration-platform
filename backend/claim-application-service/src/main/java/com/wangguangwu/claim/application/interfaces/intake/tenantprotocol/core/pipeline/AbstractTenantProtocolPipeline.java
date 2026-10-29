package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.pipeline;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeException;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.TenantProtocolConverter;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.TenantProtocolParser;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.TenantProtocolValidator;
import com.wangguangwu.claim.application.usecase.intake.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * 租户协议流水线模板，固定「验签 → 解密 → 解析 → 校验 → 转换」执行链路。
 *
 * <p>各节点提供默认实现，租户按需扩展；转换节点最终产出签收用例的
 * {@link ClaimApplicationIntakeCommand} 命令。真实租户只需覆写验签与解密并提供解析、
 * 校验、转换组件，按租户编码和协议版本注册子类。</p>
 *
 * @param <R> 租户 request 类型
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractTenantProtocolPipeline<R> implements TenantProtocolPipeline {

    private final String tenantCode;
    private final String protocolVersion;
    private final TenantProtocolParser<R> parser;
    private final TenantProtocolValidator<R> validator;
    private final TenantProtocolConverter<R> converter;

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
     * 依次执行验签、解密、解析、校验与转换。
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
        validator.validate(request);
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
}
