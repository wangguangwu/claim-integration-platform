package com.wangguangwu.claim.application.interfaces.adapter.demo;

import java.util.List;

/**
 * Demo Insurer B 租户申请报文，人员证件为嵌套结构，材料位于 evidences。
 *
 * @param claimReference B 租户外部申请编号
 * @param claimant       申请人
 * @param mainInsured    主被保险人
 * @param injured        出险人
 * @param beneficiary    领款人
 * @param evidences      材料列表
 * @author wangguangwu
 * @since 2026-10-27
 */
public record DemoInsurerBIntakeRequest(
        String claimReference,
        DemoInsurerBPerson claimant,
        DemoInsurerBPerson mainInsured,
        DemoInsurerBPerson injured,
        DemoInsurerBPerson beneficiary,
        List<DemoInsurerBEvidence> evidences) {

    /**
     * B 租户人员节点，性别使用数字编码，证件为嵌套对象。
     *
     * @param name       姓名
     * @param genderCode 性别编码
     * @param credential 证件信息
     */
    public record DemoInsurerBPerson(String name, String genderCode, DemoInsurerBCredential credential) {
    }

    /**
     * B 租户证件节点。
     *
     * @param type   证件类型
     * @param number 证件号码
     */
    public record DemoInsurerBCredential(String type, String number) {
    }

    /**
     * B 租户材料节点。
     *
     * @param documentCode    材料类型编码
     * @param fileDownloadUrl 材料下载地址
     */
    public record DemoInsurerBEvidence(String documentCode, String fileDownloadUrl) {
    }
}
