package com.wangguangwu.claim.application.interfaces.audit.messaging;

import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationEventEnvelope;
import com.wangguangwu.claim.application.usecase.audit.command.AuditSignedCommand;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 审核签收消息装配器，把 MQ 信封转换为用例命令。
 *
 * <p>报文结构变化只影响本类与消费者，用例层不依赖信封与通用 Map。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Component
public class AuditSignedMessageAssembler {

    private static final String AUDIT_SIGNED_EVENT_TYPE = "AUDIT_SIGNED";
    private static final String CASE_NOS_FIELD = "caseNos";
    private static final int MAX_CASE_NO_LENGTH = 64;

    /**
     * 校验并解析审核签收消息。
     *
     * @param envelope 消息信封
     * @return 审核签收命令
     */
    public AuditSignedCommand toCommand(ClaimApplicationEventEnvelope envelope) {
        if (envelope == null) {
            throw invalid("审核签收消息信封不能为空");
        }
        requireText(envelope.eventId(), "审核签收消息缺少事件标识");
        requireText(envelope.claimApplicationNo(), "审核签收消息缺少平台申请编号");
        requireText(envelope.tenantCode(), "审核签收消息缺少租户编码");
        if (!AUDIT_SIGNED_EVENT_TYPE.equals(envelope.eventType())) {
            throw invalid("审核签收消息事件类型不匹配，eventType=" + envelope.eventType());
        }
        List<String> caseNos = parseCaseNos(envelope.payload());
        return new AuditSignedCommand(envelope.claimApplicationNo(), envelope.tenantCode(), envelope.eventId(),
                envelope.traceId(), caseNos);
    }

    private List<String> parseCaseNos(Map<String, Object> payload) {
        Object rawCaseNos = payload == null ? null : payload.get(CASE_NOS_FIELD);
        if (!(rawCaseNos instanceof List<?> caseNoElements) || caseNoElements.isEmpty()) {
            throw invalid("审核签收消息缺少案件号列表");
        }
        Set<String> caseNos = new LinkedHashSet<>();
        for (Object element : caseNoElements) {
            if (!(element instanceof String caseNoText)) {
                throw invalid("审核签收消息的案件号必须为字符串");
            }
            String caseNo = caseNoText.trim();
            if (caseNo.isEmpty()) {
                throw invalid("审核签收消息的案件号不能为空白");
            }
            if (caseNo.length() > MAX_CASE_NO_LENGTH) {
                throw invalid("审核签收消息的案件号长度不能超过 " + MAX_CASE_NO_LENGTH);
            }
            caseNos.add(caseNo);
        }
        return List.copyOf(caseNos);
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID, message);
    }
}
