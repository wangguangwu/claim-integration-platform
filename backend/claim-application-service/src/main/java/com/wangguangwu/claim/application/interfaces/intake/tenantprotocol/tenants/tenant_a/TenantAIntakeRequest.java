package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants.tenant_a;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A 租户申请报文，人员为扁平字段，材料位于 attachments。
 *
 * <p>必填约束由 Bean Validation 注解声明，字段路径即报文路径；
 * 违规由流水线默认的校验节点统一翻译为平台业务异常。</p>
 *
 * @param requestNo   外部申请编号
 * @param applicant   申请人
 * @param insured     被保险人
 * @param accident    出险人
 * @param payee       领款人
 * @param attachments 材料列表
 * @author wangguangwu
 * @since 2026-10-27
 */
public record TenantAIntakeRequest(
        @NotBlank(message = "不能为空") String requestNo,
        @NotNull(message = "不能缺失") @Valid TenantAPerson applicant,
        @NotNull(message = "不能缺失") @Valid TenantAPerson insured,
        @NotNull(message = "不能缺失") @Valid TenantAPerson accident,
        @NotNull(message = "不能缺失") @Valid TenantAPerson payee,
        @NotEmpty(message = "不能为空") @Valid List<@NotNull(message = "不能缺失") TenantAAttachment> attachments) {

    /**
     * A 租户人员节点，性别使用单字母编码。
     *
     * @param fullName 姓名
     * @param sex      性别编码
     * @param idType   证件类型
     * @param idNo     证件号码
     */
    public record TenantAPerson(
            @NotBlank(message = "不能为空") String fullName,
            @NotBlank(message = "不能为空") String sex,
            @NotBlank(message = "不能为空") String idType,
            @NotBlank(message = "不能为空") String idNo) {
    }

    /**
     * A 租户材料节点。
     *
     * @param type      材料类型
     * @param sourceUrl 材料原始地址
     */
    public record TenantAAttachment(
            @NotBlank(message = "不能为空") String type,
            @NotBlank(message = "不能为空") String sourceUrl) {
    }
}
