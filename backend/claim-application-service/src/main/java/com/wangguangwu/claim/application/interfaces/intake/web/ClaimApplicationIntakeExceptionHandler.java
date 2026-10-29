package com.wangguangwu.claim.application.interfaces.intake.web;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeException;
import com.wangguangwu.claim.application.interfaces.intake.web.render.ClaimApplicationResponseRenderer;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一接入的异常到平台响应映射。
 *
 * <p>签收受理前失败不产生平台申请号，也不写入交互轨迹，只记录必要诊断日志。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice(basePackages = "com.wangguangwu.claim.application.interfaces.intake.web")
public class ClaimApplicationIntakeExceptionHandler {

    private static final String SYSTEM_ERROR_MESSAGE = "申请签收处理失败";

    private final ClaimApplicationResponseRenderer responseRenderer;

    /**
     * 返回调用方可处理的业务错误，并记录已脱敏的错误上下文。
     *
     * @param exception 业务异常
     * @param request   当前请求
     * @return 平台统一响应
     */
    @ExceptionHandler(ClaimApplicationIntakeException.class)
    public ResponseEntity<Object> handleBusinessException(ClaimApplicationIntakeException exception,
                                                          HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.warn("申请签收业务异常，traceId={}，errorCode={}，message={}", traceId,
                exception.getErrorCode().name(), exception.getMessage());
        return responseRenderer.renderFailure(traceId, exception.getErrorCode(), exception.getMessage());
    }

    /**
     * 记录未预期异常的链路上下文，但不记录原始租户报文和人员敏感信息。
     *
     * @param exception 未预期异常
     * @param request   当前请求
     * @return 平台统一响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleSystemException(Exception exception, HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        log.error("申请签收处理异常，traceId={}", traceId, exception);
        return responseRenderer.renderFailure(traceId, ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR,
                SYSTEM_ERROR_MESSAGE);
    }
}
