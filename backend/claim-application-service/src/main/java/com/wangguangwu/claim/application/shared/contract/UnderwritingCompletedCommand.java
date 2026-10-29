package com.wangguangwu.claim.application.shared.contract;

/**
 * 承保完成通知命令，来源可以是承保系统回调或进程内模拟承保。
 *
 * @param applicationNo 平台申请编号
 * @param requestId     通知发送方生成的稳定请求标识，重试保持不变
 * @param traceId       链路标识
 * @author wangguangwu
 * @since 2026-10-27
 */
public record UnderwritingCompletedCommand(String applicationNo, String requestId, String traceId) {
}
