package com.wangguangwu.claim.application.interfaces.underwriting.web;

import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncException;
import com.wangguangwu.claim.application.shared.interfaces.web.ApiResponse;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 承保完成回调的异常映射，优先于统一接入的全局异常处理器生效。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.wangguangwu.claim.application.interfaces.underwriting.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UnderwritingCallbackExceptionHandler {

    private static final String CALLBACK_INVALID_MESSAGE = "承保完成回调报文不合法";
    private static final String SYSTEM_ERROR_MESSAGE = "承保完成回调处理失败";

    /**
     * 按错误码映射 HTTP 状态，业务错误与系统错误分别返回 400、404 与 500。
     *
     * @param exception 业务异常
     * @param request   当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(ClaimApplicationAsyncException.class)
    public ResponseEntity<ApiResponse<Void>> handleAsyncException(ClaimApplicationAsyncException exception,
                                                                  HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.warn("承保完成回调业务异常，traceId={}，errorCode={}，message={}", traceId,
                exception.getErrorCode().name(), exception.getMessage());
        return ResponseEntity.status(statusOf(exception.getErrorCode()))
                .body(ApiResponse.failure(exception.getErrorCode().name(), exception.getMessage(), traceId));
    }

    /**
     * 报文无法解析时，视为缺少 claimApplicationNo 的回调非法请求。
     *
     * @param exception 报文解析异常
     * @param request   当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception,
                                                                     HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.warn("承保完成回调报文无法解析，traceId={}", traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(ClaimApplicationAsyncErrorCode.CALLBACK_INVALID.name(),
                        CALLBACK_INVALID_MESSAGE, traceId));
    }

    /**
     * 未预期异常统一返回系统错误，避免回调方重试时拿到框架默认报文。
     *
     * @param exception 未预期异常
     * @param request   当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(Exception exception, HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.error("承保完成回调处理异常，traceId={}", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ClaimApplicationAsyncErrorCode.CLAIM_APPLICATION_ASYNC_SYSTEM_ERROR.name(),
                        SYSTEM_ERROR_MESSAGE, traceId));
    }

    private HttpStatus statusOf(ClaimApplicationAsyncErrorCode errorCode) {
        return switch (errorCode) {
            case CALLBACK_INVALID -> HttpStatus.BAD_REQUEST;
            case CLAIM_APPLICATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CLAIM_APPLICATION_ASYNC_SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
