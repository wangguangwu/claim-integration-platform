package com.wangguangwu.claim.application.usecase.material.result;

/**
 * 材料处理结果。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum MaterialProcessOutcome {

    /**
     * 当前批次材料全部处理成功，申请已推进到已受理。
     */
    ACCEPTED,

    /**
     * 当前批次存在处理失败的材料，申请已推进到受理失败。
     */
    ACCEPT_FAILED,

    /**
     * 事件重复或状态已变化，未改变业务数据。
     */
    REUSED,

    /**
     * 同一事件已由其他消费者登记，直接跳过。
     */
    SKIPPED
}
