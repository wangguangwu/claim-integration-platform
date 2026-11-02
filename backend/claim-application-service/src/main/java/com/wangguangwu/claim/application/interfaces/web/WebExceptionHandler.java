package com.wangguangwu.claim.application.interfaces.web;

import com.wangguangwu.claim.application.shared.contract.BusinessErrorCode;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;

/**
 * 申请服务唯一的 HTTP 异常处理器，把业务异常与请求解析异常统一渲染为平台响应。
 *
 * <p>业务异常的状态码来自 {@link BusinessErrorStatusRegistry}，错误码由异常自带；
 * 字段校验失败、报文不可解析与未预期异常的错误码由当前控制器通过 {@link ApiErrorCodeProvider} 声明。
 * 日志只记录链路标识、错误码与消息，不输出请求报文和敏感字段。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionHandler {

    private static final String INVALID_REQUEST_MESSAGE = "请求报文不合法";
    private static final String SYSTEM_ERROR_MESSAGE = "系统处理失败";
    private static final String UNRESOLVED_SYSTEM_ERROR_CODE = "SYSTEM_ERROR";

    /**
     * 按业务错误码映射状态码并返回统一失败响应。
     *
     * @param exception 业务异常
     * @param request   当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception,
                                                                     HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        BusinessErrorCode errorCode = exception.getErrorCode();
        HttpStatus status = BusinessErrorStatusRegistry.statusOf(errorCode);
        log.warn("业务请求被拒绝，traceId={}，errorCode={}，message={}", traceId, errorCode.code(),
                exception.getMessage());
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(errorCode.code(), exception.getMessage(), traceId));
    }

    /**
     * 字段校验失败时按首个字段错误返回 400，避免调用方拿到系统错误码。
     *
     * @param exception     请求体校验异常
     * @param handlerMethod 当前控制器方法
     * @param request       当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(MethodArgumentNotValidException exception,
                                                                  HandlerMethod handlerMethod,
                                                                  HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(INVALID_REQUEST_MESSAGE);
        ApiErrorCodeProvider provider = providerOf(handlerMethod);
        if (provider == null) {
            log.error("请求校验失败但无法确定接口错误码，traceId={}", traceId, exception);
            return systemFailure(UNRESOLVED_SYSTEM_ERROR_CODE, traceId);
        }
        log.warn("请求报文校验失败，traceId={}，handler={}，message={}", traceId, handlerName(handlerMethod), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(provider.requestInvalidCode().code(), message, traceId));
    }

    /**
     * 报文无法解析时视为非法请求，返回 400 而非系统错误。
     *
     * @param exception     报文解析异常
     * @param handlerMethod 当前控制器方法
     * @param request       当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception,
                                                                     HandlerMethod handlerMethod,
                                                                     HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        ApiErrorCodeProvider provider = providerOf(handlerMethod);
        if (provider == null) {
            log.error("请求报文无法解析且无法确定接口错误码，traceId={}", traceId, exception);
            return systemFailure(UNRESOLVED_SYSTEM_ERROR_CODE, traceId);
        }
        log.warn("请求报文无法解析，traceId={}，handler={}", traceId, handlerName(handlerMethod));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(provider.requestInvalidCode().code(), INVALID_REQUEST_MESSAGE, traceId));
    }

    /**
     * 未预期异常统一返回系统错误，堆栈只在 ERROR 日志输出一次。
     *
     * @param exception     未预期异常
     * @param handlerMethod 当前控制器方法
     * @param request       当前请求
     * @return 统一失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(Exception exception, HandlerMethod handlerMethod,
                                                                   HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        ApiErrorCodeProvider provider = providerOf(handlerMethod);
        if (provider == null) {
            log.error("请求处理异常且无法确定接口错误码，traceId={}", traceId, exception);
            return systemFailure(UNRESOLVED_SYSTEM_ERROR_CODE, traceId);
        }
        log.error("请求处理异常，traceId={}，handler={}", traceId, handlerName(handlerMethod), exception);
        return systemFailure(provider.systemErrorCode().code(), traceId);
    }

    /**
     * 取当前控制器声明的错误码，非控制器或未实现声明时返回空。
     *
     * @param handlerMethod 当前控制器方法
     * @return 错误码声明，取不到时返回空
     */
    private ApiErrorCodeProvider providerOf(HandlerMethod handlerMethod) {
        if (handlerMethod == null) {
            return null;
        }
        Object handlerBean = handlerMethod.getBean();
        return handlerBean instanceof ApiErrorCodeProvider provider ? provider : null;
    }

    private String handlerName(HandlerMethod handlerMethod) {
        return handlerMethod == null
                ? "unknown"
                : handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
    }

    private ResponseEntity<ApiResponse<Void>> systemFailure(String errorCode, String traceId) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(errorCode, SYSTEM_ERROR_MESSAGE, traceId));
    }
}
