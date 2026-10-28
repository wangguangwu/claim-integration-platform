package com.wangguangwu.claim.application.interfaces.adapter.demo;

import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationConverter;
import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationValidation;
import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.command.Gender;
import com.wangguangwu.claim.application.usecase.command.MaterialCommand;
import com.wangguangwu.claim.application.usecase.command.PersonInfo;
import java.util.List;

/**
 * A 租户 request 转换器，把扁平人员字段与 attachments 归一化为标准命令。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class DemoInsurerARequestConverter implements TenantApplicationConverter<DemoInsurerAIntakeRequest> {

    private static final String GENDER_MALE_CODE = "M";
    private static final String GENDER_FEMALE_CODE = "F";

    /**
     * 转换为标准签收命令。
     *
     * @param tenantCode 当前租户编码
     * @param request    已校验的 A 租户 request
     * @return 标准化签收命令
     */
    @Override
    public ApplicationIntakeCommand convert(String tenantCode, DemoInsurerAIntakeRequest request) {
        return new ApplicationIntakeCommand(tenantCode, request.requestNo().trim(),
                toPerson(request.applicant()), toPerson(request.insured()), toPerson(request.accident()),
                toPerson(request.payee()), toMaterials(request.attachments()));
    }

    private PersonInfo toPerson(DemoInsurerAIntakeRequest.DemoInsurerAPerson person) {
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
            default -> throw TenantApplicationValidation.invalid("sex 仅支持 M 或 F");
        };
    }

    private List<MaterialCommand> toMaterials(List<DemoInsurerAIntakeRequest.DemoInsurerAAttachment> attachments) {
        return attachments.stream()
                .map(attachment -> new MaterialCommand(attachment.type().trim(), attachment.sourceUrl().trim()))
                .toList();
    }
}
