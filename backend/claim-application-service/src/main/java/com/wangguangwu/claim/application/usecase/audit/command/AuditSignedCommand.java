package com.wangguangwu.claim.application.usecase.audit.command;

import java.util.List;

/**
 * 审核签收命令，来自审核签收 Topic 的消息信封。
 *
 * @param claimApplicationNo 平台申请编号
 * @param tenantCode         租户编码
 * @param eventId            事件标识，同时作为交互行为组的请求标识
 * @param traceId            链路标识
 * @param caseNos            本次签收返回的案件号，已去重
 * @author wangguangwu
 * @since 2026-11-02
 */
public record AuditSignedCommand(String claimApplicationNo, String tenantCode, String eventId, String traceId,
                                 List<String> caseNos) {
}
