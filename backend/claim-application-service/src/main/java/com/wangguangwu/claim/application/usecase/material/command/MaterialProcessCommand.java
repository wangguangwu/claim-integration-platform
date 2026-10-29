package com.wangguangwu.claim.application.usecase.material.command;

/**
 * 材料处理命令，来自材料处理 Topic 的消息信封。
 *
 * @param claimApplicationNo      平台申请编号
 * @param tenantCode         租户编码
 * @param eventId            事件标识，同时作为交互行为组的请求标识
 * @param traceId            链路标识
 * @param claimApplicationAttempt 事件产生时的申请尝试次数
 * @author wangguangwu
 * @since 2026-10-27
 */
public record MaterialProcessCommand(String claimApplicationNo, String tenantCode, String eventId, String traceId,
                                     int claimApplicationAttempt) {
}
