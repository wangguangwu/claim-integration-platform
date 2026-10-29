package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;

/**
 * 基于 Bean Validation 注解的租户报文校验器，把约束违规翻译为平台业务异常。
 *
 * <p>约束声明在租户 request 类型上，本类只负责定位与消息组装：取字段路径字典序最小的一条违规，
 * 消息格式为「字段路径 + 注解文案」，不输出字段值。</p>
 *
 * @param <R> 租户 request 类型
 * @author wangguangwu
 * @since 2026-10-29
 */
@RequiredArgsConstructor
public class BeanValidationTenantProtocolValidator<R> implements TenantProtocolValidator<R> {

    private final Validator validator;

    /**
     * 校验租户 request 的注解约束，存在违规时抛出签收业务异常。
     *
     * @param request 已解析的租户 request
     */
    @Override
    public void validate(R request) {
        validator.validate(request).stream()
                .min(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .ifPresent(violation -> {
                    throw ClaimApplicationIntakeException.invalid(describe(violation));
                });
    }

    private String describe(ConstraintViolation<R> violation) {
        return violation.getPropertyPath() + " " + violation.getMessage();
    }
}
