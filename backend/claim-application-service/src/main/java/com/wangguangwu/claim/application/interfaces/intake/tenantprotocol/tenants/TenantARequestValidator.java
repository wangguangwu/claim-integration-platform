package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants;

import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.TenantProtocolFieldGuard;
import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.TenantProtocolValidator;
import java.util.List;

/**
 * A 租户报文校验器，校验四个人员节点与材料数组的必填字段。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class TenantARequestValidator implements TenantProtocolValidator<TenantAIntakeRequest> {

    /**
     * 校验 A 租户报文的必填字段与嵌套结构。
     *
     * @param request A 租户 request
     */
    @Override
    public void validate(TenantAIntakeRequest request) {
        TenantProtocolFieldGuard.requireText(request.requestNo(), "requestNo");
        validatePerson(request.applicant(), "applicant");
        validatePerson(request.insured(), "insured");
        validatePerson(request.accident(), "accident");
        validatePerson(request.payee(), "payee");
        validateAttachments(request.attachments());
    }

    private void validatePerson(TenantAIntakeRequest.TenantAPerson person, String fieldPath) {
        TenantProtocolFieldGuard.requirePresent(person, fieldPath);
        TenantProtocolFieldGuard.requireText(person.fullName(), fieldPath + ".fullName");
        TenantProtocolFieldGuard.requireText(person.sex(), fieldPath + ".sex");
        TenantProtocolFieldGuard.requireText(person.idType(), fieldPath + ".idType");
        TenantProtocolFieldGuard.requireText(person.idNo(), fieldPath + ".idNo");
    }

    private void validateAttachments(List<TenantAIntakeRequest.TenantAAttachment> attachments) {
        TenantProtocolFieldGuard.requirePresent(attachments, "attachments");
        for (int index = 0; index < attachments.size(); index++) {
            TenantAIntakeRequest.TenantAAttachment attachment = attachments.get(index);
            String fieldPath = "attachments[" + index + "]";
            TenantProtocolFieldGuard.requirePresent(attachment, fieldPath);
            TenantProtocolFieldGuard.requireText(attachment.type(), fieldPath + ".type");
            TenantProtocolFieldGuard.requireText(attachment.sourceUrl(), fieldPath + ".sourceUrl");
        }
    }
}
