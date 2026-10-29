package com.wangguangwu.claim.application.domain.integration.interaction;

/**
 * 交互行为类型，对应 {@code t_claim_interaction_log.interaction_type}。
 *
 * <p>每种交互的业务方向和固定目标系统属于交互模型本身的词汇，因此声明在枚举上，
 * 不由用例层推断。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum InteractionType {

    CLAIM_APPLICATION_INTAKE(InteractionDirection.INBOUND, "CLAIM_APPLICATION_SERVICE"),
    MATERIAL_PROCESS(InteractionDirection.INBOUND, "MATERIAL_PROCESSOR"),
    UNDERWRITING(InteractionDirection.OUTBOUND, "UNDERWRITING_SYSTEM"),
    UNDERWRITING_CALLBACK(InteractionDirection.INBOUND, "UNDERWRITING_SYSTEM"),
    AUDIT_DELIVERY(InteractionDirection.OUTBOUND, "AUDIT_SYSTEM");

    private final InteractionDirection direction;
    private final String targetSystem;

    InteractionType(InteractionDirection direction, String targetSystem) {
        this.direction = direction;
        this.targetSystem = targetSystem;
    }

    /**
     * 获取该交互相对本服务的方向。
     *
     * @return 交互方向
     */
    public InteractionDirection direction() {
        return direction;
    }

    /**
     * 获取该交互的目标外部系统或内部组件，对应 {@code t_claim_interaction_log.target_system}。
     *
     * @return 目标系统标识
     */
    public String targetSystem() {
        return targetSystem;
    }
}
