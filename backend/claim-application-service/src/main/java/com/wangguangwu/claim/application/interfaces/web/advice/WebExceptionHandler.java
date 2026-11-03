package com.wangguangwu.claim.application.interfaces.web.advice;

import com.wangguangwu.claim.application.interfaces.web.dto.response.ApiResponse;
import com.wangguangwu.claim.application.shared.exception.BusinessException;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 申请服务唯一的 HTTP 异常处理器，把业务异常与请求解析异常统一渲染为平台响应。
 *
 * <p>业务拒绝统一返回 400，字段校验失败与报文不可解析返回 400，
 * 路由或资源不存在返回 404，未预期异常返回 500。
 * 响应只区分成功与失败，失败原因通过消息返回。
 * 日志只记录链路标识与消息，不输出请求报文和敏感字段。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionHandler {

    private static final String INVALID_REQUEST_MESSAGE = "请求报文不合法";
    private static final String NOT_FOUND_MESSAGE = "请求的资源不存在";
    private static final String SYSTEM_ERROR_MESSAGE = "系统处理失败";

    /**
     * 业务拒绝统一返回 400，消息原样返回给调用方。
     *
     * @param exception 业务异常
     * @param request   当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception,
                                                                     HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.warn("业务请求被拒绝，traceId={}，message={}", traceId, exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(exception.getMessage(), traceId));
    }

    /**
     * 字段校验失败时按首个字段错误返回 400。
     *
     * @param exception 请求体校验异常
     * @param request   当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(MethodArgumentNotValidException exception,
                                                                  HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(INVALID_REQUEST_MESSAGE);
        log.warn("请求报文校验失败，traceId={}，message={}", traceId, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(message, traceId));
    }

    /**
     * 报文无法解析时视为非法请求，返回 400 而非系统错误。
     *
     * @param exception 报文解析异常
     * @param request   当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception,
                                                                     HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.warn("请求报文无法解析，traceId={}", traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(INVALID_REQUEST_MESSAGE, traceId));
    }

    /**
     * 请求的路由或静态资源不存在时返回 404，不按系统错误处理。
     *
     * @param exception 路由不存在异常
     * @param request   当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception,
                                                            HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.warn("请求的路由不存在，traceId={}，path={}", traceId, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(NOT_FOUND_MESSAGE, traceId));
    }

    /**
     * 未预期异常统一返回系统错误，堆栈只在 ERROR 日志输出一次。
     *
     * @param exception 未预期异常
     * @param request   当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(Exception exception,
                                                                   HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.error("请求处理异常，traceId={}", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(SYSTEM_ERROR_MESSAGE, traceId));
    }
}
