package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants;

import com.wangguangwu.claim.application.domain.claim.Gender;
import com.wangguangwu.claim.application.domain.claim.PersonInfo;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.TenantProtocolConverter;
import com.wangguangwu.claim.application.usecase.intake.command.ApplicationIntakeCommand;
import java.util.List;

/**
 * A 租户 request 转换器，把扁平人员字段与 attachments 归一化为平台受理命令。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class TenantARequestConverter implements TenantProtocolConverter<TenantAIntakeRequest> {

    private static final String GENDER_MALE_CODE = "M";
    private static final String GENDER_FEMALE_CODE = "F";

    /**
     * 转换为签收用例的标准命令。
     *
     * @param tenantCode 当前租户编码
     * @param request    已校验的 A 租户 request
     * @return 尚未分配平台申请编号的受理命令
     */
    @Override
    public ApplicationIntakeCommand convert(String tenantCode, TenantAIntakeRequest request) {
        return ApplicationIntakeCommand.builder().tenantCode(tenantCode)
                .externalApplicationNo(request.requestNo().trim()).applicant(toPerson(request.applicant()))
                .primaryInsured(toPerson(request.insured())).accidentPerson(toPerson(request.accident()))
                .payee(toPerson(request.payee())).materials(toMaterials(request.attachments())).build();
    }

    private PersonInfo toPerson(TenantAIntakeRequest.TenantAPerson person) {
        return new PersonInfo(person.fullName().trim(), toGender(person.sex()), person.idType().trim(),
                person.idNo().trim());
    }

    /**
     * 将 A 租户单字母性别编码归一化为标准性别。
     *
     * @param sex 租户性别编码
     * @return 标准性别
     */
    private Gender toGender(String sex) {
        return switch (sex.trim()) {
            case GENDER_MALE_CODE -> Gender.MALE;
            case GENDER_FEMALE_CODE -> Gender.FEMALE;
            default -> throw ApplicationIntakeException.invalid("sex 仅支持 M 或 F");
        };
    }

    private List<MaterialSubmission> toMaterials(List<TenantAIntakeRequest.TenantAAttachment> attachments) {
        return attachments.stream()
                .map(attachment -> new MaterialSubmission(attachment.type().trim(), attachment.sourceUrl().trim()))
                .toList();
    }
}
