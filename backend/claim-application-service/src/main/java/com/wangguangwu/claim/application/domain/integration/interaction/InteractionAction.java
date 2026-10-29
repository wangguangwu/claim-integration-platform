package com.wangguangwu.claim.application.domain.integration.interaction;

/**
 * 交互事件的阶段动作。
 *
 * <p>动作到交互结果的映射是交互模型的固定词汇，因此声明在枚举上。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum InteractionAction {

    STARTED(InteractionResultStatus.STARTED),
    COMPLETED(InteractionResultStatus.SUCCESS),
    REUSED(InteractionResultStatus.SUCCESS),
    FAILED(InteractionResultStatus.FAILED);

    private final InteractionResultStatus resultStatus;

    InteractionAction(InteractionResultStatus resultStatus) {
        this.resultStatus = resultStatus;
    }

    /**
     * 获取该动作对应的交互结果状态，对应 {@code t_claim_interaction_log.result_status}。
     *
     * @return 交互结果状态
     */
    public InteractionResultStatus resultStatus() {
        return resultStatus;
    }
}
