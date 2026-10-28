package com.wangguangwu.claim.application.interfaces.adapter.demo;

import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationValidator;
import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationValidation;
import java.util.List;

/**
 * A 租户报文校验器，校验四个人员节点与材料数组的必填字段。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class DemoInsurerARequestValidator implements TenantApplicationValidator<DemoInsurerAIntakeRequest> {

    /**
     * 校验 A 租户报文的必填字段与嵌套结构。
     *
     * @param request A 租户 request
     */
    @Override
    public void validate(DemoInsurerAIntakeRequest request) {
        TenantApplicationValidation.requireText(request.requestNo(), "requestNo");
        validatePerson(request.applicant(), "applicant");
        validatePerson(request.insured(), "insured");
        validatePerson(request.accident(), "accident");
        validatePerson(request.payee(), "payee");
        validateAttachments(request.attachments());
    }

    private void validatePerson(DemoInsurerAIntakeRequest.DemoInsurerAPerson person, String fieldPath) {
        TenantApplicationValidation.requirePresent(person, fieldPath);
        TenantApplicationValidation.requireText(person.fullName(), fieldPath + ".fullName");
        TenantApplicationValidation.requireText(person.sex(), fieldPath + ".sex");
        TenantApplicationValidation.requireText(person.idType(), fieldPath + ".idType");
        TenantApplicationValidation.requireText(person.idNo(), fieldPath + ".idNo");
    }

    private void validateAttachments(List<DemoInsurerAIntakeRequest.DemoInsurerAAttachment> attachments) {
        TenantApplicationValidation.requirePresent(attachments, "attachments");
        for (int index = 0; index < attachments.size(); index++) {
            DemoInsurerAIntakeRequest.DemoInsurerAAttachment attachment = attachments.get(index);
            String fieldPath = "attachments[" + index + "]";
            TenantApplicationValidation.requirePresent(attachment, fieldPath);
            TenantApplicationValidation.requireText(attachment.type(), fieldPath + ".type");
            TenantApplicationValidation.requireText(attachment.sourceUrl(), fieldPath + ".sourceUrl");
        }
    }
}
