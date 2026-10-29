package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants;

import com.wangguangwu.claim.application.domain.claim.Gender;
import com.wangguangwu.claim.application.domain.claim.PersonInfo;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.TenantProtocolConverter;
import com.wangguangwu.claim.application.usecase.intake.command.ApplicationIntakeCommand;
import java.util.List;

/**
 * B 租户 request 转换器，把嵌套证件字段、数字性别码与 evidences 归一化为平台受理命令。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class TenantBRequestConverter implements TenantProtocolConverter<TenantBIntakeRequest> {

    private static final String GENDER_MALE_CODE = "1";
    private static final String GENDER_FEMALE_CODE = "2";

    /**
     * 转换为签收用例的标准命令。
     *
     * @param tenantCode 当前租户编码
     * @param request    已校验的 B 租户 request
     * @return 尚未分配平台申请编号的受理命令
     */
    @Override
    public ApplicationIntakeCommand convert(String tenantCode, TenantBIntakeRequest request) {
        return new ApplicationIntakeCommand(tenantCode, request.claimReference().trim(), toPerson(request.claimant()),
                toPerson(request.mainInsured()), toPerson(request.injured()), toPerson(request.beneficiary()),
                toMaterials(request.evidences()));
    }

    private PersonInfo toPerson(TenantBIntakeRequest.TenantBPerson person) {
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
            default -> throw ApplicationIntakeException.invalid("genderCode 仅支持 1 或 2");
        };
    }

    private List<MaterialSubmission> toMaterials(List<TenantBIntakeRequest.TenantBEvidence> evidences) {
        return evidences.stream()
                .map(evidence -> new MaterialSubmission(evidence.documentCode().trim(),
                        evidence.fileDownloadUrl().trim()))
                .toList();
    }
}
