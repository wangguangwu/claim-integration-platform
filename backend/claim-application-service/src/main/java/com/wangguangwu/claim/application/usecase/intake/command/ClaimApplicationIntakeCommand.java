package com.wangguangwu.claim.application.usecase.intake.command;

import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import java.util.List;
import lombok.Builder;

/**
 * 理赔申请签收命令，租户私有报文经协议流水线归一化后的受理输入。
 *
 * <p>命令只携带标准化后的业务事实，平台申请编号、状态与材料批次由签收用例分配。
 * 四类人员各用 {@link Applicant}、{@link PrimaryInsured}、{@link AccidentPerson}、{@link Payee}
 * 独立角色类型表达，建造者按角色命名装配，角色互换无法通过编译。</p>
 *
 * @param tenantCode            租户编码
 * @param externalClaimApplicationNo 租户侧申请编号
 * @param applicant             申请人
 * @param primaryInsured        主被保险人
 * @param accidentPerson        出险人
 * @param payee                 领款人
 * @param materials             本次提交的材料
 * @author wangguangwu
 * @since 2026-10-29
 */
@Builder
public record ClaimApplicationIntakeCommand(
        String tenantCode,
        String externalClaimApplicationNo,
        Applicant applicant,
        PrimaryInsured primaryInsured,
        AccidentPerson accidentPerson,
        Payee payee,
        List<MaterialSubmission> materials) {}
