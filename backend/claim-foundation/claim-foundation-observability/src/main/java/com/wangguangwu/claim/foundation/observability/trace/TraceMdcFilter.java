package com.wangguangwu.claim.foundation.observability.trace;

import com.wangguangwu.claim.foundation.common.logging.MdcContext;
import com.wangguangwu.claim.foundation.common.logging.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 链路标识过滤器：把请求头或生成的链路标识写入 MDC，请求结束后清理。
 *
 * <p>请求未携带 {@code X-Trace-Id} 时生成 UUID，并包装请求让下游控制器与异常处理器
 * 读取到同一个标识，保证响应 {@code traceId} 与日志一致。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public class TraceMdcFilter extends OncePerRequestFilter {

    /**
     * 绑定链路标识，执行完请求后移除。
     *
     * @param request     当前请求
     * @param response    当前响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        HttpServletRequest requestToUse = request;
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
            requestToUse = new TraceIdRequestWrapper(request, traceId);
        }
        MdcContext.put(MdcKeys.TRACE_ID, traceId);
        try {
            filterChain.doFilter(requestToUse, response);
        } finally {
            MdcContext.remove(MdcKeys.TRACE_ID);
        }
    }

    /**
     * 让下游通过请求头读取生成的链路标识。
     */
    private static final class TraceIdRequestWrapper extends HttpServletRequestWrapper {

        private final String traceId;

        private TraceIdRequestWrapper(HttpServletRequest request, String traceId) {
            super(request);
            this.traceId = traceId;
        }

        @Override
        public String getHeader(String name) {
            if (TraceHeaders.TRACE_ID.equalsIgnoreCase(name)) {
                return traceId;
            }
            return super.getHeader(name);
        }
    }
}
