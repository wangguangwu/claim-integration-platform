package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants.tenant_a;

import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeException;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.stage.TenantProtocolConverter;
import com.wangguangwu.claim.application.usecase.intake.command.ClaimApplicationIntakeCommand;
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
    public ClaimApplicationIntakeCommand convert(String tenantCode, TenantAIntakeRequest request) {
        return ClaimApplicationIntakeCommand.builder().tenantCode(tenantCode)
                .externalClaimApplicationNo(request.requestNo().trim()).applicant(toApplicant(request.applicant()))
                .primaryInsured(toPrimaryInsured(request.insured())).accidentPerson(toAccidentPerson(request.accident()))
                .payee(toPayee(request.payee())).materials(toMaterials(request.attachments())).build();
    }

    private Applicant toApplicant(TenantAIntakeRequest.TenantAPerson person) {
        return new Applicant(person.fullName().trim(), toGender(person.sex()), person.idType().trim(),
                person.idNo().trim());
    }

    private PrimaryInsured toPrimaryInsured(TenantAIntakeRequest.TenantAPerson person) {
        return new PrimaryInsured(person.fullName().trim(), toGender(person.sex()), person.idType().trim(),
                person.idNo().trim());
    }

    private AccidentPerson toAccidentPerson(TenantAIntakeRequest.TenantAPerson person) {
        return new AccidentPerson(person.fullName().trim(), toGender(person.sex()), person.idType().trim(),
                person.idNo().trim());
    }

    private Payee toPayee(TenantAIntakeRequest.TenantAPerson person) {
        return new Payee(person.fullName().trim(), toGender(person.sex()), person.idType().trim(),
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
            default -> throw ClaimApplicationIntakeException.invalid("sex 仅支持 M 或 F");
        };
    }

    private List<MaterialSubmission> toMaterials(List<TenantAIntakeRequest.TenantAAttachment> attachments) {
        return attachments.stream()
                .map(attachment -> new MaterialSubmission(attachment.type().trim(), attachment.sourceUrl().trim()))
                .toList();
    }
}
