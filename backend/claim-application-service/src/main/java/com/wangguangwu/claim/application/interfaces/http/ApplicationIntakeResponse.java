package com.wangguangwu.claim.application.interfaces.http;

/**
 * 理赔申请统一接入成功响应数据。
 *
 * @param applicationNo 平台申请编号
 * @author wangguangwu
 * @since 2026-10-27
 */
public record ApplicationIntakeResponse(String applicationNo) {
}
