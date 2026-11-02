package com.wangguangwu.claim.application.interfaces.audit.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.shared.contract.message.AuditSignedMessage;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 审核签收报文解析器单元测试：验证严格反序列化与错误码。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("审核签收报文解析器")
class AuditSignedMessageParserTest {

    private final AuditSignedMessageParser parser = new AuditSignedMessageParser(new ObjectMapper());

    @Test
    @DisplayName("完整信封可解析，历史字段与契约外字段被忽略")
    void parse_fullEnvelope_ignoresLegacyAndUnknownFields() {
        AuditSignedMessage message = parser.parse(bytes("""
                {
                  "eventId": "evt-1",
                  "eventType": "AUDIT_SIGNED",
                  "eventVersion": 1,
                  "claimApplicationNo": "A2026110200000001",
                  "tenantCode": "tenant-a",
                  "occurredAt": "2026-11-02T10:00:00+08:00",
                  "traceId": "trace-1",
                  "source": "audit-system",
                  "payload": {"caseNos": ["CASE-001", "CASE-002"]}
                }
                """));

        assertThat(message.eventId()).isEqualTo("evt-1");
        assertThat(message.claimApplicationNo()).isEqualTo("A2026110200000001");
        assertThat(message.tenantCode()).isEqualTo("tenant-a");
        assertThat(message.traceId()).isEqualTo("trace-1");
        assertThat(message.payload().caseNos()).containsExactly("CASE-001", "CASE-002");
    }

    @Test
    @DisplayName("案件号为数字：解析失败并返回 AUDIT_SIGNED_INVALID")
    void parse_numericCaseNo_throwsInvalid() {
        assertInvalid("""
                {
                  "eventId": "evt-2",
                  "eventType": "AUDIT_SIGNED",
                  "claimApplicationNo": "A2026110200000001",
                  "tenantCode": "tenant-a",
                  "traceId": "trace-2",
                  "payload": {"caseNos": [1001]}
                }
                """);
    }

    @Test
    @DisplayName("报文不是合法 JSON：解析失败并返回 AUDIT_SIGNED_INVALID")
    void parse_malformedJson_throwsInvalid() {
        assertInvalid("{\"eventId\":");
    }

    @Test
    @DisplayName("报文为空：返回 AUDIT_SIGNED_INVALID")
    void parse_emptyPayload_throwsInvalid() {
        assertThatThrownBy(() -> parser.parse(new byte[0]))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID);
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> parser.parse(bytes(json)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID);
    }

    private byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
