package com.wangguangwu.claim.application.shared.interfaces.web;

/**
 * 申请服务的统一 HTTP 响应结构。
 *
 * @param code 业务结果码
 * @param message 响应说明
 * @param traceId 链路标识
 * @param data 响应数据
 * @author wangguangwu
 * @since 2026-10-27
 */
public record ApiResponse<T>(String code, String message, String traceId, T data) {

    /**
     * 构建成功响应。
     *
     * @param traceId 链路标识
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(String traceId, T data) {
        return new ApiResponse<>("SUCCESS", "success", traceId, data);
    }

    /**
     * 构建失败响应。
     *
     * @param code 业务错误码
     * @param message 响应说明
     * @param traceId 链路标识
     * @return 失败响应
     */
    public static ApiResponse<Void> failure(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, traceId, null);
    }
}
