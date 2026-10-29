package com.wangguangwu.claim.application.domain.integration.outbox;

import java.util.Arrays;

/**
 * 理赔申请异步事件类型，枚举名即消息中的 {@code eventType}。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ApplicationEventType {

    /**
     * 首次签收完成，触发材料处理。
     */
    APPLICATION_CREATED("materialProcess-out-0"),

    /**
     * 受理失败后重新申请，触发材料处理。
     */
    APPLICATION_REAPPLIED("materialProcess-out-0"),

    /**
     * 已具备审核条件，触发审核交付。
     */
    AUDIT_DELIVERY_REQUESTED("auditDelivery-out-0");

    private final String bindingName;

    ApplicationEventType(String bindingName) {
        this.bindingName = bindingName;
    }

    /**
     * 获取该事件类型对应的 Stream 输出绑定名。
     *
     * @return 输出绑定名
     */
    public String bindingName() {
        return bindingName;
    }

    /**
     * 按消息中的事件类型解析枚举，未知类型直接拒绝投递。
     *
     * @param eventType 消息事件类型
     * @return 事件类型
     */
    public static ApplicationEventType from(String eventType) {
        return Arrays.stream(values())
                .filter(applicationEventType -> applicationEventType.name().equals(eventType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的理赔申请事件类型，eventType=" + eventType));
    }
}
