package com.wangguangwu.claim.application.domain.integration.interaction;

/**
 * 交互事件的结果状态，对应 {@code t_claim_interaction_log.result_status}。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
public enum InteractionResultStatus {

    STARTED,
    SUCCESS,
    FAILED
}
