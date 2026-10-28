package com.wangguangwu.claim.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.application.Application;
import com.wangguangwu.claim.application.domain.interaction.InteractionLog;
import com.wangguangwu.claim.application.domain.material.Material;
import com.wangguangwu.claim.application.domain.material.MaterialBatchNoGenerator;
import com.wangguangwu.claim.application.domain.outbox.OutboxEvent;
import com.wangguangwu.claim.application.usecase.command.MaterialCommand;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 构建申请签收所需的材料、交互事件与 Outbox 记录，不访问数据库与 Redis。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Service
public class ApplicationIntakeRecordFactory {

    private static final String INTERACTION_TYPE = "APPLICATION_INTAKE";
    private static final String DIRECTION_INBOUND = "INBOUND";
    private static final String TARGET_SYSTEM = "CLAIM_APPLICATION_SERVICE";
    private static final String RESULT_STARTED = "STARTED";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String ACTION_STARTED = "STARTED";
    private static final String ACTION_COMPLETED = "COMPLETED";
    private static final String ACTION_REUSED = "REUSED";
    private static final String EVENT_CREATED = "APPLICATION_CREATED";
    private static final String EVENT_REAPPLIED = "APPLICATION_REAPPLIED";
    private static final String OUTBOX_PENDING = "PENDING";
    private static final int EVENT_VERSION = 1;
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper objectMapper;
    private final MaterialBatchNoGenerator materialBatchNoGenerator;

    public ApplicationIntakeRecordFactory(ObjectMapper objectMapper,
                                          MaterialBatchNoGenerator materialBatchNoGenerator) {
        this.objectMapper = objectMapper;
        this.materialBatchNoGenerator = materialBatchNoGenerator;
    }

    /**
     * 为同一次签收创建共用批次编号的材料记录。
     *
     * @param applicationNo 平台申请编号
     * @param materialCommands 标准化材料命令
     * @return 待持久化材料
     */
    public List<Material> createMaterials(String applicationNo, List<MaterialCommand> materialCommands) {
        String batchNo = materialBatchNoGenerator.nextBatchNo();
        return materialCommands.stream()
                .map(materialCommand -> Material.createPending(applicationNo, batchNo, materialCommand.materialType(),
                        materialCommand.originalFileUrl()))
                .toList();
    }

    /**
     * 构建首次签收事件，载荷不包含人员敏感信息。
     *
     * @param application Application 聚合
     * @return 待投递事件
     */
    public OutboxEvent applicationCreatedEvent(Application application) {
        return newOutboxEvent(application, EVENT_CREATED);
    }

    /**
     * 构建重新申请事件，载荷不包含人员敏感信息。
     *
     * @param application Application 聚合
     * @return 待投递事件
     */
    public OutboxEvent applicationReappliedEvent(Application application) {
        return newOutboxEvent(application, EVENT_REAPPLIED);
    }

    /**
     * 构建行为组开始事件。
     *
     * @param applicationNo 平台申请编号
     * @param interactionNo 行为组编号
     * @param requestId 请求标识
     * @param traceId 链路标识
     * @return 交互事件
     */
    public InteractionLog interactionStarted(String applicationNo, String interactionNo, String requestId,
                                             String traceId) {
        return new InteractionLog(applicationNo, interactionNo, INTERACTION_TYPE, ACTION_STARTED, DIRECTION_INBOUND,
                TARGET_SYSTEM, requestId, traceId, RESULT_STARTED, null, null, now());
    }

    /**
     * 构建行为组成功结束事件。
     *
     * @param applicationNo 平台申请编号
     * @param interactionNo 行为组编号
     * @param requestId 请求标识
     * @param traceId 链路标识
     * @param completed 本次请求是否改变了业务数据；false 表示幂等复用
     * @return 交互事件
     */
    public InteractionLog interactionFinished(String applicationNo, String interactionNo, String requestId,
                                              String traceId, boolean completed) {
        String action = completed ? ACTION_COMPLETED : ACTION_REUSED;
        return new InteractionLog(applicationNo, interactionNo, INTERACTION_TYPE, action, DIRECTION_INBOUND,
                TARGET_SYSTEM, requestId, traceId, RESULT_SUCCESS, null, null, now());
    }

    /**
     * 将标准对象序列化为 MySQL JSON 列的内容。
     *
     * @param value 待序列化对象
     * @return JSON 文本
     */
    public String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("申请接入数据序列化失败", exception);
        }
    }

    private OutboxEvent newOutboxEvent(Application application, String eventType) {
        String payload = serialize(Map.of(
                "applicationNo", application.getApplicationNo(),
                "tenantCode", application.getTenantCode(),
                "eventType", eventType,
                "applicationAttempt", application.getApplicationAttempt()));
        return new OutboxEvent(UUID.randomUUID().toString(), eventType, EVENT_VERSION, application.getApplicationNo(),
                application.getTenantCode(), payload, OUTBOX_PENDING);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE_ID);
    }
}
