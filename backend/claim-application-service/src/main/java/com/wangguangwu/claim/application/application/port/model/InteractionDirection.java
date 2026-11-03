package com.wangguangwu.claim.application.application.port.model;

/**
 * 交互方向，对应 {@code t_claim_interaction_log.direction}。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
public enum InteractionDirection {

    /** 外部系统或上游组件向本服务发起的交互。 */
    INBOUND,

    /** 本服务向外部系统或下游组件发起的交互。 */
    OUTBOUND
}
