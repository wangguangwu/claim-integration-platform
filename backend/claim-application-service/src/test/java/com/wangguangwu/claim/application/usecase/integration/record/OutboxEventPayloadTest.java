package com.wangguangwu.claim.application.usecase.integration.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.usecase.integration.outbox.model.AuditDeliveryRequestedOutboxPayload;
import com.wangguangwu.claim.application.usecase.integration.outbox.model.MaterialProcessOutboxPayload;
import com.wangguangwu.claim.application.usecase.integration.outbox.model.OutboxEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Outbox 存储载荷测试：锁定工厂写入的字段集合与历史载荷兼容性。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("Outbox 存储载荷")
class OutboxEventPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClaimApplicationLifecycleRecordFactory recordFactory =
            new ClaimApplicationLifecycleRecordFactory(objectMapper);

    @Test
    @DisplayName("材料处理事件只存申请尝试次数与链路标识")
    void materialProcessEvent_storesAttemptAndTraceIdOnly() throws JsonProcessingException {
        OutboxEvent event = recordFactory.claimApplicationCreatedEvent(application(2), "trace-1");

        JsonNode payload = objectMapper.readTree(event.payload());

        assertThat(fieldNames(payload)).containsExactlyInAnyOrder("claimApplicationAttempt", "traceId");
        assertThat(payload.get("claimApplicationAttempt").asInt()).isEqualTo(2);
        assertThat(payload.get("traceId").asText()).isEqualTo("trace-1");
        assertThat(event.eventType()).isEqualTo("CLAIM_APPLICATION_CREATED");
        assertThat(event.eventVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("审核交付事件只存链路标识")
    void auditDeliveryEvent_storesTraceIdOnly() throws JsonProcessingException {
        OutboxEvent event = recordFactory.auditDeliveryRequestedEvent(application(2), "trace-2");

        JsonNode payload = objectMapper.readTree(event.payload());

        assertThat(fieldNames(payload)).containsExactly("traceId");
        assertThat(payload.get("traceId").asText()).isEqualTo("trace-2");
        assertThat(event.eventType()).isEqualTo("AUDIT_DELIVERY_REQUESTED");
    }

    @Test
    @DisplayName("历史材料处理载荷的重复字段被忽略")
    void materialProcessOutboxPayload_readsHistoricalPayload() throws JsonProcessingException {
        String historical = """
                {
                  "claimApplicationNo": "A2026110200000001",
                  "tenantCode": "tenant-a",
                  "eventType": "CLAIM_APPLICATION_CREATED",
                  "claimApplicationAttempt": 3,
                  "traceId": "trace-3"
                }
                """;

        MaterialProcessOutboxPayload payload = objectMapper.readValue(historical, MaterialProcessOutboxPayload.class);

        assertThat(payload.claimApplicationAttempt()).isEqualTo(3);
        assertThat(payload.traceId()).isEqualTo("trace-3");
    }

    @Test
    @DisplayName("历史审核交付载荷的重复字段被忽略")
    void auditDeliveryOutboxPayload_readsHistoricalPayload() throws JsonProcessingException {
        String historical = """
                {
                  "claimApplicationNo": "A2026110200000001",
                  "tenantCode": "tenant-a",
                  "claimApplicationAttempt": 3,
                  "traceId": "trace-4"
                }
                """;

        AuditDeliveryRequestedOutboxPayload payload = objectMapper.readValue(historical,
                AuditDeliveryRequestedOutboxPayload.class);

        assertThat(payload.traceId()).isEqualTo("trace-4");
    }

    private ClaimApplication application(int attempt) {
        ClaimApplication application = mock(ClaimApplication.class);
        when(application.getClaimApplicationNo()).thenReturn("A2026110200000001");
        when(application.getTenantCode()).thenReturn("tenant-a");
        when(application.getClaimApplicationAttempt()).thenReturn(attempt);
        return application;
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
