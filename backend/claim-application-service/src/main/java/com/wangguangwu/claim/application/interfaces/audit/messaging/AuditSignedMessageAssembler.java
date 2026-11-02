package com.wangguangwu.claim.application.interfaces.audit.messaging;

import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.shared.contract.message.AuditSignedMessage;
import com.wangguangwu.claim.application.shared.contract.message.AuditSignedPayload;
import com.wangguangwu.claim.application.usecase.audit.command.AuditSignedCommand;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 审核签收消息装配器，把 MQ 消息转换为用例命令。
 *
 * <p>报文结构变化只影响本类与消费者，用例层不依赖消息契约。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Component
public class AuditSignedMessageAssembler {

    private static final String AUDIT_SIGNED_EVENT_TYPE = "AUDIT_SIGNED";
    private static final int MAX_CASE_NO_LENGTH = 64;

    /**
     * 校验并解析审核签收消息。
     *
     * @param message 审核签收消息
     * @return 审核签收命令
     */
    public AuditSignedCommand toCommand(AuditSignedMessage message) {
        if (message == null) {
            throw invalid("审核签收消息不能为空");
        }
        requireText(message.eventId(), "审核签收消息缺少事件标识");
        requireText(message.claimApplicationNo(), "审核签收消息缺少平台申请编号");
        requireText(message.tenantCode(), "审核签收消息缺少租户编码");
        if (!AUDIT_SIGNED_EVENT_TYPE.equals(message.eventType())) {
            throw invalid("审核签收消息事件类型不匹配，eventType=" + message.eventType());
        }
        List<String> caseNos = parseCaseNos(message.payload());
        return new AuditSignedCommand(message.claimApplicationNo(), message.tenantCode(), message.eventId(),
                message.traceId(), caseNos);
    }

    private List<String> parseCaseNos(AuditSignedPayload payload) {
        if (payload == null || payload.caseNos() == null || payload.caseNos().isEmpty()) {
            throw invalid("审核签收消息缺少案件号列表");
        }
        Set<String> caseNos = new LinkedHashSet<>();
        for (String caseNoText : payload.caseNos()) {
            if (caseNoText == null) {
                throw invalid("审核签收消息的案件号不能为空");
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
