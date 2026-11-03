package com.wangguangwu.claim.application.shared.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 消息契约 JSON 测试：锁定三类消息的字段集合与反序列化边界。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("消息契约 JSON")
class MessageContractJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("材料处理消息只序列化 4 个公共字段与申请尝试次数")
    void materialProcessMessage_serializesMinimalFieldSet() throws JsonProcessingException {
        MaterialProcessMessage message = new MaterialProcessMessage("evt-1", "A2026110200000001", "tenant-a",
                "trace-1", new MaterialProcessPayload(2));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("eventId", "claimApplicationNo", "tenantCode",
                "traceId", "payload");
        assertThat(fieldNames(json.get("payload"))).containsExactly("claimApplicationAttempt");
        assertThat(json.has("eventType")).isFalse();
        assertThat(json.has("eventVersion")).isFalse();
        assertThat(json.has("occurredAt")).isFalse();
    }

    @Test
    @DisplayName("审核交付消息只序列化 4 个公共字段，不携带载荷")
    void auditDeliveryRequestedMessage_serializesCommonFieldsOnly() throws JsonProcessingException {
        AuditDeliveryRequestedMessage message = new AuditDeliveryRequestedMessage("evt-2", "A2026110200000001",
                "tenant-a", "trace-2");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("eventId", "claimApplicationNo", "tenantCode",
                "traceId");
        assertThat(json.has("payload")).isFalse();
        assertThat(json.has("eventType")).isFalse();
        assertThat(json.has("eventVersion")).isFalse();
        assertThat(json.has("occurredAt")).isFalse();
    }

    @Test
    @DisplayName("审核签收消息序列化 4 个公共字段、事件类型与案件号载荷")
    void auditSignedMessage_serializesEventTypeAndCaseNos() throws JsonProcessingException {
        AuditSignedMessage message = new AuditSignedMessage("evt-3", "AUDIT_SIGNED", "A2026110200000001", "tenant-a",
                "trace-3", new AuditSignedPayload(List.of("CASE-001")));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("eventId", "eventType", "claimApplicationNo",
                "tenantCode", "traceId", "payload");
        assertThat(fieldNames(json.get("payload"))).containsExactly("caseNos");
        assertThat(json.has("eventVersion")).isFalse();
        assertThat(json.has("occurredAt")).isFalse();
    }

    @Test
    @DisplayName("审核签收消息容忍 eventVersion、occurredAt 与契约外字段")
    void auditSignedMessage_ignoresUnknownFields() throws JsonProcessingException {
        String json = """
                {
                  "eventId": "evt-4",
                  "eventType": "AUDIT_SIGNED",
                  "eventVersion": 1,
                  "claimApplicationNo": "A2026110200000001",
                  "tenantCode": "tenant-a",
                  "occurredAt": "2026-11-02T10:00:00+08:00",
                  "traceId": "trace-4",
                  "payload": {"caseNos": [" CASE-001 ", "CASE-002"], "extra": "ignored"},
                  "unknownField": "ignored"
                }
                """;

        AuditSignedMessage message = objectMapper.readValue(json, AuditSignedMessage.class);

        assertThat(message.eventId()).isEqualTo("evt-4");
        assertThat(message.eventType()).isEqualTo("AUDIT_SIGNED");
        assertThat(message.claimApplicationNo()).isEqualTo("A2026110200000001");
        assertThat(message.tenantCode()).isEqualTo("tenant-a");
        assertThat(message.traceId()).isEqualTo("trace-4");
        assertThat(message.payload().caseNos()).containsExactly(" CASE-001 ", "CASE-002");
    }

    @Test
    @DisplayName("审核签收消息的 null 案件号列表交给装配器处理")
    void auditSignedMessage_nullCaseNos_isKeptNull() throws JsonProcessingException {
        String json = """
                {
                  "eventId": "evt-5",
                  "eventType": "AUDIT_SIGNED",
                  "claimApplicationNo": "A2026110200000001",
                  "tenantCode": "tenant-a",
                  "traceId": "trace-5",
                  "payload": {"caseNos": null}
                }
                """;

        AuditSignedMessage message = objectMapper.readValue(json, AuditSignedMessage.class);

        assertThat(message.payload().caseNos()).isNull();
    }

    @Test
    @DisplayName("案件号元素不是字符串：反序列化失败")
    void auditSignedMessage_numericCaseNo_failsDeserialization() {
        String json = """
                {
                  "eventId": "evt-6",
                  "eventType": "AUDIT_SIGNED",
                  "claimApplicationNo": "A2026110200000001",
                  "tenantCode": "tenant-a",
                  "traceId": "trace-6",
                  "payload": {"caseNos": [1001]}
                }
                """;

        assertThatThrownBy(() -> objectMapper.readValue(json, AuditSignedMessage.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
