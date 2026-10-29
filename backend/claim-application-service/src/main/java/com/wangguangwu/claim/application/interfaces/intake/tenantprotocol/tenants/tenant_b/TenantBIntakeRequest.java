package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants.tenant_b;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * B 租户申请报文，人员证件为嵌套结构，材料位于 evidences。
 *
 * <p>必填约束由 Bean Validation 注解声明，字段路径即报文路径；
 * 违规由 {@code BeanValidationTenantProtocolValidator} 统一翻译为平台业务异常。</p>
 *
 * @param claimReference 外部申请编号
 * @param claimant       申请人
 * @param mainInsured    主被保险人
 * @param injured        出险人
 * @param beneficiary    领款人
 * @param evidences      材料列表
 * @author wangguangwu
 * @since 2026-10-27
 */
public record TenantBIntakeRequest(
        @NotBlank(message = "不能为空") String claimReference,
        @NotNull(message = "不能缺失") @Valid TenantBPerson claimant,
        @NotNull(message = "不能缺失") @Valid TenantBPerson mainInsured,
        @NotNull(message = "不能缺失") @Valid TenantBPerson injured,
        @NotNull(message = "不能缺失") @Valid TenantBPerson beneficiary,
        @NotEmpty(message = "不能为空") @Valid List<@NotNull(message = "不能缺失") TenantBEvidence> evidences) {

    /**
     * B 租户人员节点，性别使用数字编码，证件为嵌套对象。
     *
     * @param name       姓名
     * @param genderCode 性别编码
     * @param credential 证件信息
     */
    public record TenantBPerson(
            @NotBlank(message = "不能为空") String name,
            @NotBlank(message = "不能为空") String genderCode,
            @NotNull(message = "不能缺失") @Valid TenantBCredential credential) {
    }

    /**
     * B 租户证件节点。
     *
     * @param type   证件类型
     * @param number 证件号码
     */
    public record TenantBCredential(
            @NotBlank(message = "不能为空") String type,
            @NotBlank(message = "不能为空") String number) {
    }

    /**
     * B 租户材料节点。
     *
     * @param documentCode    材料类型编码
     * @param fileDownloadUrl 材料下载地址
     */
    public record TenantBEvidence(
            @NotBlank(message = "不能为空") String documentCode,
            @NotBlank(message = "不能为空") String fileDownloadUrl) {
    }
}
