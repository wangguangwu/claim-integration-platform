package com.wangguangwu.claim.application.usecase.integration.interaction.model;

/**
 * 交互事件的结果状态，对应 {@code t_claim_interaction_log.result_status}。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
public enum InteractionResultStatus {

    /** 已记录交互开始，但尚未产生最终结果。 */
    STARTED,

    /** 交互成功完成，包含复用已有结果的幂等处理。 */
    SUCCESS,

    /** 交互未成功完成，需要按具体链路决定重试或人工处理。 */
    FAILED
}
