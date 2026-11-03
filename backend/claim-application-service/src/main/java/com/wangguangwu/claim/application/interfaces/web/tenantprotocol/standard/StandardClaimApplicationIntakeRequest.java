package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.standard;

import com.wangguangwu.claim.application.domain.claim.person.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 真实租户协议接入前使用的平台标准理赔申请报文。
 *
 * @param externalClaimApplicationNo 租户侧申请编号
 * @param claimApplicationNo 平台申请编号，重新申请与补材时可选携带
 * @param applicant 申请人
 * @param primaryInsured 主被保险人
 * @param accidentPerson 出险人
 * @param payee 领款人
 * @param materials 本次提交的材料
 * @author wangguangwu
 * @since 2026-11-03
 */
public record StandardClaimApplicationIntakeRequest(
        @NotBlank(message = "不能为空") String externalClaimApplicationNo,
        String claimApplicationNo,
        @NotNull(message = "不能缺失") @Valid StandardPerson applicant,
        @NotNull(message = "不能缺失") @Valid StandardPerson primaryInsured,
        @NotNull(message = "不能缺失") @Valid StandardPerson accidentPerson,
        @NotNull(message = "不能缺失") @Valid StandardPerson payee,
        @NotEmpty(message = "不能为空") @Valid List<@NotNull(message = "不能缺失") StandardMaterial> materials) {

    /**
     * 平台标准人员字段。
     *
     * @param name 姓名
     * @param gender 性别
     * @param certificateType 证件类型
     * @param certificateNo 证件号码
     */
    public record StandardPerson(
            @NotBlank(message = "不能为空") String name,
            @NotNull(message = "不能缺失") Gender gender,
            @NotBlank(message = "不能为空") String certificateType,
            @NotBlank(message = "不能为空") String certificateNo) {
    }

    /**
     * 平台标准材料字段。
     *
     * @param materialType 材料类型
     * @param originalFileUrl 材料原始地址
     */
    public record StandardMaterial(
            @NotBlank(message = "不能为空") String materialType,
            @NotBlank(message = "不能为空") String originalFileUrl) {
    }
}
