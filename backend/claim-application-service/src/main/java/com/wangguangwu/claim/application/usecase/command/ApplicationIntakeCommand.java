package com.wangguangwu.claim.application.usecase.command;

import java.util.List;

/**
 * 租户协议适配后的标准理赔申请签收命令。
 *
 * @param tenantCode            租户编码
 * @param externalApplicationNo 租户外部申请编号
 * @param applicant             申请人信息
 * @param primaryInsured        主被保险人信息
 * @param accidentPerson        出险人信息
 * @param payee                 领款人信息
 * @param materials             首次材料
 * @author wangguangwu
 * @since 2026-10-27
 */
public record ApplicationIntakeCommand(
        String tenantCode,
        String externalApplicationNo,
        PersonInfo applicant,
        PersonInfo primaryInsured,
        PersonInfo accidentPerson,
        PersonInfo payee,
        List<MaterialCommand> materials) {
}
