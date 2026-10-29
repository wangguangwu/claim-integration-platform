package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.tenants.tenant_a;

import java.util.List;

/**
 * A 租户申请报文，人员为扁平字段，材料位于 attachments。
 *
 * @param requestNo   外部申请编号
 * @param applicant   申请人
 * @param insured     被保险人
 * @param accident    出险人
 * @param payee       领款人
 * @param attachments 材料列表
 * @author wangguangwu
 * @since 2026-10-27
 */
public record TenantAIntakeRequest(
        String requestNo,
        TenantAPerson applicant,
        TenantAPerson insured,
        TenantAPerson accident,
        TenantAPerson payee,
        List<TenantAAttachment> attachments) {

    /**
     * A 租户人员节点，性别使用单字母编码。
     *
     * @param fullName 姓名
     * @param sex      性别编码
     * @param idType   证件类型
     * @param idNo     证件号码
     */
    public record TenantAPerson(String fullName, String sex, String idType, String idNo) {
    }

    /**
     * A 租户材料节点。
     *
     * @param type      材料类型
     * @param sourceUrl 材料原始地址
     */
    public record TenantAAttachment(String type, String sourceUrl) {
    }
}
