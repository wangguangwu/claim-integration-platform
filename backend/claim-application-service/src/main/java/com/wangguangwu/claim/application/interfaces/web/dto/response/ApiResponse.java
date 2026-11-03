package com.wangguangwu.claim.application.interfaces.web.dto.response;

/**
 * 申请服务的统一 HTTP 响应结构。
 *
 * @param code 结果码，只有成功与失败两种取值
 * @param message 响应说明
 * @param traceId 链路标识
 * @param data 响应数据
 * @author wangguangwu
 * @since 2026-10-27
 */
public record ApiResponse<T>(String code, String message, String traceId, T data) {

    private static final String SUCCESS_CODE = "0";

    private static final String FAILURE_CODE = "1";

    /**
     * 构建成功响应。
     *
     * @param traceId 链路标识
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(String traceId, T data) {
        return new ApiResponse<>(SUCCESS_CODE, "success", traceId, data);
    }

    /**
     * 构建失败响应。
     *
     * @param message 响应说明
     * @param traceId 链路标识
     * @return 失败响应
     */
    public static ApiResponse<Void> failure(String message, String traceId) {
        return new ApiResponse<>(FAILURE_CODE, message, traceId, null);
    }
}
