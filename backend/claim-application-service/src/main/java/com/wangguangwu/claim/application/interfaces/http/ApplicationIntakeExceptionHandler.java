package com.wangguangwu.claim.application.interfaces.http;

import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeException;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import com.wangguangwu.claim.foundation.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一接入的异常到租户响应映射。
 *
 * <p>签收受理前失败不产生平台申请号，也不写入交互轨迹，只记录必要诊断日志。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RestControllerAdvice
public class ApplicationIntakeExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationIntakeExceptionHandler.class);
    private static final String SYSTEM_ERROR_MESSAGE = "申请签收处理失败";

    private final TenantApplicationResponseRouter responseRouter;

    public ApplicationIntakeExceptionHandler(TenantApplicationResponseRouter responseRouter) {
        this.responseRouter = responseRouter;
    }

    /**
     * 返回调用方可处理的业务错误，并记录已脱敏的错误上下文。
     *
     * @param exception 业务异常
     * @param request   当前请求
     * @return 租户协议响应
     */
    @ExceptionHandler(ApplicationIntakeException.class)
    public ResponseEntity<Object> handleBusinessException(ApplicationIntakeException exception,
                                                          HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        LOGGER.warn("申请签收业务异常，traceId={}，errorCode={}，message={}", traceId,
                exception.getErrorCode().name(), exception.getMessage());
        return responseRouter.renderFailure(TenantContext.get(), traceId,
                exception.getErrorCode(), exception.getMessage());
    }

    /**
     * 记录未预期异常的链路上下文，但不记录原始租户报文和人员敏感信息。
     *
     * @param exception 未预期异常
     * @param request   当前请求
     * @return 租户协议响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleSystemException(Exception exception, HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        LOGGER.error("申请签收处理异常，traceId={}", traceId, exception);
        return responseRouter.renderFailure(TenantContext.get(), traceId,
                ApplicationIntakeErrorCode.APPLICATION_INTAKE_SYSTEM_ERROR, SYSTEM_ERROR_MESSAGE);
    }
}
