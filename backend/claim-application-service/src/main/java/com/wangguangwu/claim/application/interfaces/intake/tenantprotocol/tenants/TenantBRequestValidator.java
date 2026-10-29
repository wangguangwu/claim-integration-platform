package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants;

import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.TenantProtocolFieldGuard;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.TenantProtocolValidator;
import java.util.List;

/**
 * B 租户报文校验器，校验四个人员节点、嵌套证件与材料数组的必填字段。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class TenantBRequestValidator implements TenantProtocolValidator<TenantBIntakeRequest> {

    /**
     * 校验 B 租户报文的必填字段与嵌套结构。
     *
     * @param request B 租户 request
     */
    @Override
    public void validate(TenantBIntakeRequest request) {
        TenantProtocolFieldGuard.requireText(request.claimReference(), "claimReference");
        validatePerson(request.claimant(), "claimant");
        validatePerson(request.mainInsured(), "mainInsured");
        validatePerson(request.injured(), "injured");
        validatePerson(request.beneficiary(), "beneficiary");
        validateEvidences(request.evidences());
    }

    private void validatePerson(TenantBIntakeRequest.TenantBPerson person, String fieldPath) {
        TenantProtocolFieldGuard.requirePresent(person, fieldPath);
        TenantProtocolFieldGuard.requireText(person.name(), fieldPath + ".name");
        TenantProtocolFieldGuard.requireText(person.genderCode(), fieldPath + ".genderCode");
        validateCredential(person.credential(), fieldPath + ".credential");
    }

    private void validateCredential(TenantBIntakeRequest.TenantBCredential credential, String fieldPath) {
        TenantProtocolFieldGuard.requirePresent(credential, fieldPath);
        TenantProtocolFieldGuard.requireText(credential.type(), fieldPath + ".type");
        TenantProtocolFieldGuard.requireText(credential.number(), fieldPath + ".number");
    }

    private void validateEvidences(List<TenantBIntakeRequest.TenantBEvidence> evidences) {
        TenantProtocolFieldGuard.requirePresent(evidences, "evidences");
        for (int index = 0; index < evidences.size(); index++) {
            TenantBIntakeRequest.TenantBEvidence evidence = evidences.get(index);
            String fieldPath = "evidences[" + index + "]";
            TenantProtocolFieldGuard.requirePresent(evidence, fieldPath);
            TenantProtocolFieldGuard.requireText(evidence.documentCode(), fieldPath + ".documentCode");
            TenantProtocolFieldGuard.requireText(evidence.fileDownloadUrl(), fieldPath + ".fileDownloadUrl");
        }
    }
}
