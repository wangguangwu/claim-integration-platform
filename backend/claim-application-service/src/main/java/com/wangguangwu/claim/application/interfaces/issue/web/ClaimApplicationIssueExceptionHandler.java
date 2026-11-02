package com.wangguangwu.claim.application.interfaces.issue.web;

import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueException;
import com.wangguangwu.claim.application.interfaces.intake.web.render.ClaimApplicationIssueResponseRenderer;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 问题件链路的异常到平台响应映射。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice(basePackageClasses = ClaimApplicationIssueIntakeController.class)
public class ClaimApplicationIssueExceptionHandler {

    private static final String SYSTEM_ERROR_MESSAGE = "问题件处理失败";
    private static final String INVALID_REQUEST_MESSAGE = "问题件请求报文不合法";

    private final ClaimApplicationIssueResponseRenderer responseRenderer;

    /**
     * 返回调用方可处理的业务错误，并记录已脱敏的错误上下文。
     *
     * @param exception 业务异常
     * @param request   当前请求
     * @return 平台统一响应
     */
    @ExceptionHandler(ClaimApplicationIssueException.class)
    public ResponseEntity<Object> handleBusinessException(ClaimApplicationIssueException exception,
                                                          HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.warn("问题件业务异常，traceId={}，errorCode={}，message={}", traceId,
                exception.getErrorCode().name(), exception.getMessage());
        return responseRenderer.renderFailure(traceId, exception.getErrorCode(), exception.getMessage());
    }

    /**
     * 字段校验失败时按首个字段错误返回 400，避免调用方拿到系统错误码。
     *
     * @param exception 请求体校验异常
     * @param request   当前请求
     * @return 平台统一响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleInvalidRequest(MethodArgumentNotValidException exception,
                                                       HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(INVALID_REQUEST_MESSAGE);
        log.warn("问题件请求校验失败，traceId={}，message={}", traceId, message);
        return responseRenderer.renderFailure(traceId, ClaimApplicationIssueErrorCode.ISSUE_INTAKE_INVALID, message);
    }

    /**
     * 报文无法解析时视为非法请求，返回 400 而非系统错误。
     *
     * @param exception 报文解析异常
     * @param request   当前请求
     * @return 平台统一响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleUnreadableMessage(HttpMessageNotReadableException exception,
                                                          HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.warn("问题件请求报文无法解析，traceId={}", traceId);
        return responseRenderer.renderFailure(traceId, ClaimApplicationIssueErrorCode.ISSUE_INTAKE_INVALID,
                INVALID_REQUEST_MESSAGE);
    }

    /**
     * 记录未预期异常的链路上下文，但不记录原始请求报文和敏感信息。
     *
     * @param exception 未预期异常
     * @param request   当前请求
     * @return 平台统一响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleSystemException(Exception exception, HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.error("问题件处理异常，traceId={}", traceId, exception);
        return responseRenderer.renderFailure(traceId, ClaimApplicationIssueErrorCode.ISSUE_SYSTEM_ERROR,
                SYSTEM_ERROR_MESSAGE);
    }
}
