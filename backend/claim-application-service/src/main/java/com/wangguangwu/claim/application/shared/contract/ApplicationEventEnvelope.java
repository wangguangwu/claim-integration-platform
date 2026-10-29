package com.wangguangwu.claim.application.shared.contract;

import java.util.Map;

/**
 * 002 冻结的理赔申请异步消息信封，字段名不允许修改。
 *
 * <p>载荷使用通用 Map 承载，避免消费侧消息转换器依赖具体 JSON 节点实现。</p>
 *
 * @param eventId      事件标识
 * @param eventType    事件类型
 * @param eventVersion 事件版本
 * @param applicationNo 平台申请编号
 * @param tenantCode   租户编码
 * @param occurredAt   事件发生时间，ISO-8601 带时区偏移
 * @param traceId      链路标识
 * @param payload      非敏感业务载荷
 * @author wangguangwu
 * @since 2026-10-27
 */
public record ApplicationEventEnvelope(
        String eventId,
        String eventType,
        int eventVersion,
        String applicationNo,
        String tenantCode,
        String occurredAt,
        String traceId,
        Map<String, Object> payload) {
}
