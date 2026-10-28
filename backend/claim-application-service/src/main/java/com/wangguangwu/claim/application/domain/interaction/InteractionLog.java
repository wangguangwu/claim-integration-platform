package com.wangguangwu.claim.application.domain.interaction;

import java.time.LocalDateTime;

/**
 * 一次系统交互中的单个已发生事件。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public record InteractionLog(
        String applicationNo,
        String interactionNo,
        String interactionType,
        String action,
        String direction,
        String targetSystem,
        String requestId,
        String traceId,
        String resultStatus,
        String errorCode,
        String errorMessage,
        LocalDateTime occurredAt) {
}
