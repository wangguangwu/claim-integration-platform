package com.wangguangwu.claim.application.interfaces.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wangguangwu.claim.application.shared.exception.BusinessException;
import com.wangguangwu.claim.application.application.exception.ClaimApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.shared.message.AuditSignedMessage;
import com.wangguangwu.claim.application.shared.message.AuditSignedPayload;
import com.wangguangwu.claim.application.application.command.AuditSignedCommand;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 审核签收消息装配器单元测试：验证案件号格式校验与去重。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("审核签收消息装配器")
class AuditSignedMessageAssemblerTest {

    private static final String CLAIM_APPLICATION_NO = "A2026110200000001";
    private static final String TENANT_CODE = "tenant-a";
    private static final String EVENT_ID = "evt-018-001";
    private static final String TRACE_ID = "trace-018-001";

    private final AuditSignedMessageAssembler assembler = new AuditSignedMessageAssembler();

    @Test
    @DisplayName("合法载荷：案件号去空白并去重后装配为命令")
    void toCommand_validPayload_trimsAndDeduplicatesCaseNos() {
        AuditSignedCommand command = assembler.toCommand(message("AUDIT_SIGNED",
                new AuditSignedPayload(List.of(" CASE-001 ", "CASE-002", "CASE-001"))));

        assertThat(command.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(command.tenantCode()).isEqualTo(TENANT_CODE);
        assertThat(command.eventId()).isEqualTo(EVENT_ID);
        assertThat(command.traceId()).isEqualTo(TRACE_ID);
        assertThat(command.caseNos()).containsExactly("CASE-001", "CASE-002");
    }

    @Test
    @DisplayName("事件类型不匹配：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_wrongEventType_throwsInvalid() {
        assertInvalid(message("CLAIM_APPLICATION_CREATED", new AuditSignedPayload(List.of("CASE-001"))));
    }

    @Test
    @DisplayName("缺少案件号列表：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_missingCaseNos_throwsInvalid() {
        assertInvalid(message("AUDIT_SIGNED", null));
        assertInvalid(message("AUDIT_SIGNED", new AuditSignedPayload(null)));
        assertInvalid(message("AUDIT_SIGNED", new AuditSignedPayload(List.of())));
    }

    @Test
    @DisplayName("案件号为空：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_nullCaseNo_throwsInvalid() {
        assertInvalid(message("AUDIT_SIGNED", new AuditSignedPayload(Arrays.asList("CASE-001", null))));
    }

    @Test
    @DisplayName("案件号为空白：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_blankCaseNo_throwsInvalid() {
        assertInvalid(message("AUDIT_SIGNED", new AuditSignedPayload(List.of("   "))));
    }

    @Test
    @DisplayName("案件号超过 64 字符：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_overlongCaseNo_throwsInvalid() {
        assertInvalid(message("AUDIT_SIGNED", new AuditSignedPayload(List.of("C".repeat(65)))));
    }

    @Test
    @DisplayName("缺少平台申请编号：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_missingClaimApplicationNo_throwsInvalid() {
        AuditSignedMessage message = new AuditSignedMessage(EVENT_ID, "AUDIT_SIGNED", " ", TENANT_CODE, TRACE_ID,
                new AuditSignedPayload(List.of("CASE-001")));

        assertInvalid(message);
    }

    @Test
    @DisplayName("缺少租户编码：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_missingTenantCode_throwsInvalid() {
        AuditSignedMessage message = new AuditSignedMessage(EVENT_ID, "AUDIT_SIGNED", CLAIM_APPLICATION_NO, " ",
                TRACE_ID, new AuditSignedPayload(List.of("CASE-001")));

        assertInvalid(message);
    }

    @Test
    @DisplayName("缺少事件标识：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_missingEventId_throwsInvalid() {
        AuditSignedMessage message = new AuditSignedMessage(" ", "AUDIT_SIGNED", CLAIM_APPLICATION_NO, TENANT_CODE,
                TRACE_ID, new AuditSignedPayload(List.of("CASE-001")));

        assertInvalid(message);
    }

    private AuditSignedMessage message(String eventType, AuditSignedPayload payload) {
        return new AuditSignedMessage(EVENT_ID, eventType, CLAIM_APPLICATION_NO, TENANT_CODE, TRACE_ID, payload);
    }

    private void assertInvalid(AuditSignedMessage message) {
        assertThatThrownBy(() -> assembler.toCommand(message))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID);
    }
}
