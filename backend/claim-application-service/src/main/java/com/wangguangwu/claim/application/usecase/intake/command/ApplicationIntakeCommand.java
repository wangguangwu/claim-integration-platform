package com.wangguangwu.claim.application.usecase.intake.command;

import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.domain.claim.PersonInfo;
import java.util.List;

/**
 * 理赔申请签收命令，租户私有报文经协议流水线归一化后的受理输入。
 *
 * <p>命令只携带标准化后的业务事实，平台申请编号、状态与材料批次由签收用例分配。</p>
 *
 * @param tenantCode            租户编码
 * @param externalApplicationNo 租户侧申请编号
 * @param applicant             申请人
 * @param primaryInsured        主被保险人
 * @param accidentPerson        出险人
 * @param payee                 领款人
 * @param materials             本次提交的材料
 * @author wangguangwu
 * @since 2026-10-29
 */
public record ApplicationIntakeCommand(
        String tenantCode,
        String externalApplicationNo,
        PersonInfo applicant,
        PersonInfo primaryInsured,
        PersonInfo accidentPerson,
        PersonInfo payee,
        List<MaterialSubmission> materials) {
}
