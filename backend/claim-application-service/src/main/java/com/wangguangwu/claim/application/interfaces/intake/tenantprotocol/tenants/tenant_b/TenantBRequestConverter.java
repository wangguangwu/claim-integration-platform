package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants.tenant_b;

import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.TenantProtocolConverter;
import com.wangguangwu.claim.application.usecase.intake.command.ClaimApplicationIntakeCommand;
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
    public ClaimApplicationIntakeCommand convert(String tenantCode, TenantBIntakeRequest request) {
        return ClaimApplicationIntakeCommand.builder().tenantCode(tenantCode)
                .externalApplicationNo(request.claimReference().trim()).applicant(toApplicant(request.claimant()))
                .primaryInsured(toPrimaryInsured(request.mainInsured()))
                .accidentPerson(toAccidentPerson(request.injured())).payee(toPayee(request.beneficiary()))
                .materials(toMaterials(request.evidences())).build();
    }

    private Applicant toApplicant(TenantBIntakeRequest.TenantBPerson person) {
        return new Applicant(person.name().trim(), toGender(person.genderCode()),
                person.credential().type().trim(), person.credential().number().trim());
    }

    private PrimaryInsured toPrimaryInsured(TenantBIntakeRequest.TenantBPerson person) {
        return new PrimaryInsured(person.name().trim(), toGender(person.genderCode()),
                person.credential().type().trim(), person.credential().number().trim());
    }

    private AccidentPerson toAccidentPerson(TenantBIntakeRequest.TenantBPerson person) {
        return new AccidentPerson(person.name().trim(), toGender(person.genderCode()),
                person.credential().type().trim(), person.credential().number().trim());
    }

    private Payee toPayee(TenantBIntakeRequest.TenantBPerson person) {
        return new Payee(person.name().trim(), toGender(person.genderCode()),
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
