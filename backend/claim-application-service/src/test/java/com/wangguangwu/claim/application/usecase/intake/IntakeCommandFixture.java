package com.wangguangwu.claim.application.usecase.intake;

import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.usecase.intake.command.ClaimApplicationIntakeCommand;
import java.util.List;

/**
 * 签收用例测试数据构造器，提供固定的命令与人员信息。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
final class IntakeCommandFixture {

    private static final Applicant APPLICANT = new Applicant("张三", Gender.MALE, "ID_CARD", "110101199001011234");
    private static final PrimaryInsured PRIMARY_INSURED = new PrimaryInsured("李四", Gender.FEMALE, "ID_CARD",
            "110101199002022345");
    private static final List<MaterialSubmission> MATERIALS = List.of(
            new MaterialSubmission("POLICY", "https://example.com/policy.pdf"));

    private IntakeCommandFixture() {
    }

    /**
     * 构造签收命令。
     *
     * @param tenantCode  租户编码
     * @param externalNo  外部申请编号
     * @return 签收命令
     */
    static ClaimApplicationIntakeCommand command(String tenantCode, String externalNo) {
        return ClaimApplicationIntakeCommand.builder()
                .tenantCode(tenantCode)
                .externalClaimApplicationNo(externalNo)
                .applicant(APPLICANT)
                .primaryInsured(PRIMARY_INSURED)
                .accidentPerson(null)
                .payee(null)
                .materials(MATERIALS)
                .build();
    }
}
