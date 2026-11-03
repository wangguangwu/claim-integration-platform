package com.wangguangwu.claim.application.usecase.audit.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 审核签收命令，来自审核签收 Topic 的消息信封或等待审核滞留补偿。
 *
 * @param claimApplicationNo 平台申请编号
 * @param tenantCode         租户编码
 * @param eventId            事件标识，同时作为交互行为组的请求标识
 * @param traceId            链路标识
 * @param caseNos            本次签收返回的案件号，已去空白、去重
 * @author wangguangwu
 * @since 2026-11-02
 */
public record AuditSignedCommand(String claimApplicationNo, String tenantCode, String eventId, String traceId,
                                 List<String> caseNos) {

    /** 单条案件号的最大长度，与数据库列宽一致。 */
    private static final int MAX_CASE_NO_LENGTH = 64;

    /**
     * 规范化案件号：去空白、去重并校验非空与长度。
     *
     * <p>MQ 装配器负责报文级错误码，补偿链路直接构造命令时依赖本构造器获得相同约束。</p>
     */
    public AuditSignedCommand {
        if (caseNos == null || caseNos.isEmpty()) {
            throw new IllegalArgumentException("审核签收命令的案件号列表不能为空");
        }
        Set<String> normalizedCaseNos = new LinkedHashSet<>();
        for (String caseNoText : caseNos) {
            if (caseNoText == null) {
                throw new IllegalArgumentException("审核签收命令的案件号不能为空");
            }
            String caseNo = caseNoText.trim();
            if (caseNo.isEmpty()) {
                throw new IllegalArgumentException("审核签收命令的案件号不能为空白");
            }
            if (caseNo.length() > MAX_CASE_NO_LENGTH) {
                throw new IllegalArgumentException("审核签收命令的案件号长度不能超过 " + MAX_CASE_NO_LENGTH);
            }
            normalizedCaseNos.add(caseNo);
        }
        caseNos = List.copyOf(normalizedCaseNos);
    }
}
