package com.wangguangwu.claim.application.usecase.integration.interaction.model;

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

    /** 租户或上游系统向理赔申请服务提交签收请求。 */
    CLAIM_APPLICATION_INTAKE(InteractionDirection.INBOUND, "CLAIM_APPLICATION_SERVICE"),

    /** 材料处理器向理赔申请服务反馈材料处理结果。 */
    MATERIAL_PROCESS(InteractionDirection.INBOUND, "MATERIAL_PROCESSOR"),

    /** 理赔申请服务向自动承保系统发起承保请求或查询。 */
    UNDERWRITING(InteractionDirection.OUTBOUND, "UNDERWRITING_SYSTEM"),

    /** 自动承保系统向理赔申请服务回调承保完成结果。 */
    UNDERWRITING_CALLBACK(InteractionDirection.INBOUND, "UNDERWRITING_SYSTEM"),

    /** 理赔申请服务向审核系统交付已满足审核条件的申请。 */
    AUDIT_DELIVERY(InteractionDirection.OUTBOUND, "AUDIT_SYSTEM"),

    /** 审核系统回传签收结果及案件号。 */
    AUDIT_SIGNED(InteractionDirection.INBOUND, "AUDIT_SYSTEM");

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
