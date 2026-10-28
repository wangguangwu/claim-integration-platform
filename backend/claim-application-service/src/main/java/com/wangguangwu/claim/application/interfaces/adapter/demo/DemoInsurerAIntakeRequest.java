package com.wangguangwu.claim.application.interfaces.adapter.demo;

import java.util.List;

/**
 * Demo Insurer A 租户申请报文，人员为扁平字段，材料位于 attachments。
 *
 * @param requestNo   A 租户外部申请编号
 * @param applicant   申请人
 * @param insured     被保险人
 * @param accident    出险人
 * @param payee       领款人
 * @param attachments 材料列表
 * @author wangguangwu
 * @since 2026-10-27
 */
public record DemoInsurerAIntakeRequest(
        String requestNo,
        DemoInsurerAPerson applicant,
        DemoInsurerAPerson insured,
        DemoInsurerAPerson accident,
        DemoInsurerAPerson payee,
        List<DemoInsurerAAttachment> attachments) {

    /**
     * A 租户人员节点，性别使用单字母编码。
     *
     * @param fullName 姓名
     * @param sex      性别编码
     * @param idType   证件类型
     * @param idNo     证件号码
     */
    public record DemoInsurerAPerson(String fullName, String sex, String idType, String idNo) {
    }

    /**
     * A 租户材料节点。
     *
     * @param type      材料类型
     * @param sourceUrl 材料原始地址
     */
    public record DemoInsurerAAttachment(String type, String sourceUrl) {
    }
}
