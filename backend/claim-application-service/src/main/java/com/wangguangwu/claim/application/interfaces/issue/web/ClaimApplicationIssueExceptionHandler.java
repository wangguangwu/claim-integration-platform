package com.wangguangwu.claim.application.interfaces.issue.web;

import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueException;
import com.wangguangwu.claim.application.interfaces.intake.web.render.ClaimApplicationIssueResponseRenderer;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
