package com.wangguangwu.claim.application.interfaces.adapter.demo;

import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationValidation;
import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationValidator;
import java.util.List;

/**
 * B 租户报文校验器，校验四个人员节点、嵌套证件与材料数组的必填字段。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class DemoInsurerBRequestValidator implements TenantApplicationValidator<DemoInsurerBIntakeRequest> {

    /**
     * 校验 B 租户报文的必填字段与嵌套结构。
     *
     * @param request B 租户 request
     */
    @Override
    public void validate(DemoInsurerBIntakeRequest request) {
        TenantApplicationValidation.requireText(request.claimReference(), "claimReference");
        validatePerson(request.claimant(), "claimant");
        validatePerson(request.mainInsured(), "mainInsured");
        validatePerson(request.injured(), "injured");
        validatePerson(request.beneficiary(), "beneficiary");
        validateEvidences(request.evidences());
    }

    private void validatePerson(DemoInsurerBIntakeRequest.DemoInsurerBPerson person, String fieldPath) {
        TenantApplicationValidation.requirePresent(person, fieldPath);
        TenantApplicationValidation.requireText(person.name(), fieldPath + ".name");
        TenantApplicationValidation.requireText(person.genderCode(), fieldPath + ".genderCode");
        validateCredential(person.credential(), fieldPath + ".credential");
    }

    private void validateCredential(DemoInsurerBIntakeRequest.DemoInsurerBCredential credential,
                                    String fieldPath) {
        TenantApplicationValidation.requirePresent(credential, fieldPath);
        TenantApplicationValidation.requireText(credential.type(), fieldPath + ".type");
        TenantApplicationValidation.requireText(credential.number(), fieldPath + ".number");
    }

    private void validateEvidences(List<DemoInsurerBIntakeRequest.DemoInsurerBEvidence> evidences) {
        TenantApplicationValidation.requirePresent(evidences, "evidences");
        for (int index = 0; index < evidences.size(); index++) {
            DemoInsurerBIntakeRequest.DemoInsurerBEvidence evidence = evidences.get(index);
            String fieldPath = "evidences[" + index + "]";
            TenantApplicationValidation.requirePresent(evidence, fieldPath);
            TenantApplicationValidation.requireText(evidence.documentCode(), fieldPath + ".documentCode");
            TenantApplicationValidation.requireText(evidence.fileDownloadUrl(), fieldPath + ".fileDownloadUrl");
        }
    }
}
