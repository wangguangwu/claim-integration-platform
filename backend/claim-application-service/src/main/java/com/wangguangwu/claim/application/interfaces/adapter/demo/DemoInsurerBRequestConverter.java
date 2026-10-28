package com.wangguangwu.claim.application.interfaces.adapter.demo;

import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationConverter;
import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationValidation;
import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.command.Gender;
import com.wangguangwu.claim.application.usecase.command.MaterialCommand;
import com.wangguangwu.claim.application.usecase.command.PersonInfo;
import java.util.List;

/**
 * B 租户 request 转换器，把嵌套证件字段、数字性别码与 evidences 归一化为标准命令。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class DemoInsurerBRequestConverter implements TenantApplicationConverter<DemoInsurerBIntakeRequest> {

    private static final String GENDER_MALE_CODE = "1";
    private static final String GENDER_FEMALE_CODE = "2";

    /**
     * 转换为标准签收命令。
     *
     * @param tenantCode 当前租户编码
     * @param request    已校验的 B 租户 request
     * @return 标准化签收命令
     */
    @Override
    public ApplicationIntakeCommand convert(String tenantCode, DemoInsurerBIntakeRequest request) {
        return new ApplicationIntakeCommand(tenantCode, request.claimReference().trim(),
                toPerson(request.claimant()), toPerson(request.mainInsured()), toPerson(request.injured()),
                toPerson(request.beneficiary()), toMaterials(request.evidences()));
    }

    private PersonInfo toPerson(DemoInsurerBIntakeRequest.DemoInsurerBPerson person) {
        return new PersonInfo(person.name().trim(), toGender(person.genderCode()),
                person.credential().type().trim(), person.credential().number().trim());
    }

    /**
     * 将 B 租户数字性别码归一化为标准性别。
     *
     * @param genderCode 租户性别编码
     * @return 标准性别
     */
    private Gender toGender(String genderCode) {
        return switch (genderCode.trim()) {
            case GENDER_MALE_CODE -> Gender.MALE;
            case GENDER_FEMALE_CODE -> Gender.FEMALE;
            default -> throw TenantApplicationValidation.invalid("genderCode 仅支持 1 或 2");
        };
    }

    private List<MaterialCommand> toMaterials(List<DemoInsurerBIntakeRequest.DemoInsurerBEvidence> evidences) {
        return evidences.stream()
                .map(evidence -> new MaterialCommand(evidence.documentCode().trim(),
                        evidence.fileDownloadUrl().trim()))
                .toList();
    }
}
