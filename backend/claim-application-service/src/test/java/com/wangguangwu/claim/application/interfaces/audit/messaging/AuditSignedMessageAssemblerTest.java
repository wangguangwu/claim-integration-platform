package com.wangguangwu.claim.application.interfaces.audit.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncException;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationEventEnvelope;
import com.wangguangwu.claim.application.usecase.audit.command.AuditSignedCommand;
import java.util.List;
import java.util.Map;
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
        AuditSignedCommand command = assembler.toCommand(
                envelope("AUDIT_SIGNED", Map.of("caseNos", List.of(" CASE-001 ", "CASE-002", "CASE-001"))));

        assertThat(command.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(command.tenantCode()).isEqualTo(TENANT_CODE);
        assertThat(command.eventId()).isEqualTo(EVENT_ID);
        assertThat(command.traceId()).isEqualTo(TRACE_ID);
        assertThat(command.caseNos()).containsExactly("CASE-001", "CASE-002");
    }

    @Test
    @DisplayName("事件类型不匹配：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_wrongEventType_throwsInvalid() {
        assertInvalid(envelope("CLAIM_APPLICATION_CREATED", Map.of("caseNos", List.of("CASE-001"))));
    }

    @Test
    @DisplayName("缺少案件号列表：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_missingCaseNos_throwsInvalid() {
        assertInvalid(envelope("AUDIT_SIGNED", Map.of()));
        assertInvalid(envelope("AUDIT_SIGNED", Map.of("caseNos", List.of())));
    }

    @Test
    @DisplayName("案件号非字符串：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_caseNoNotString_throwsInvalid() {
        assertInvalid(envelope("AUDIT_SIGNED", Map.of("caseNos", List.of(1001))));
    }

    @Test
    @DisplayName("案件号为空白：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_blankCaseNo_throwsInvalid() {
        assertInvalid(envelope("AUDIT_SIGNED", Map.of("caseNos", List.of("   "))));
    }

    @Test
    @DisplayName("案件号超过 64 字符：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_overlongCaseNo_throwsInvalid() {
        assertInvalid(envelope("AUDIT_SIGNED", Map.of("caseNos", List.of("C".repeat(65)))));
    }

    @Test
    @DisplayName("缺少平台申请编号：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_missingClaimApplicationNo_throwsInvalid() {
        ClaimApplicationEventEnvelope envelope = new ClaimApplicationEventEnvelope(EVENT_ID, "AUDIT_SIGNED", 1,
                " ", TENANT_CODE, "2026-11-02T10:00:00+08:00", TRACE_ID, Map.of("caseNos", List.of("CASE-001")));

        assertInvalid(envelope);
    }

    @Test
    @DisplayName("缺少事件标识：抛出 AUDIT_SIGNED_INVALID")
    void toCommand_missingEventId_throwsInvalid() {
        ClaimApplicationEventEnvelope envelope = new ClaimApplicationEventEnvelope(" ", "AUDIT_SIGNED", 1,
                CLAIM_APPLICATION_NO, TENANT_CODE, "2026-11-02T10:00:00+08:00", TRACE_ID,
                Map.of("caseNos", List.of("CASE-001")));

        assertInvalid(envelope);
    }

    private ClaimApplicationEventEnvelope envelope(String eventType, Map<String, Object> payload) {
        return new ClaimApplicationEventEnvelope(EVENT_ID, eventType, 1, CLAIM_APPLICATION_NO, TENANT_CODE,
                "2026-11-02T10:00:00+08:00", TRACE_ID, payload);
    }

    private void assertInvalid(ClaimApplicationEventEnvelope envelope) {
        assertThatThrownBy(() -> assembler.toCommand(envelope))
                .isInstanceOf(ClaimApplicationAsyncException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID);
    }
}
