package com.wangguangwu.claim.foundation.tenant.filter;

import com.wangguangwu.claim.foundation.common.logging.MdcContext;
import com.wangguangwu.claim.foundation.common.logging.MdcKeys;
import com.wangguangwu.claim.foundation.tenant.context.TenantContext;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import com.wangguangwu.claim.foundation.tenant.protocol.TenantHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 从请求头填充租户上下文与日志 MDC，请求结束后清理。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public class TenantContextFilter extends OncePerRequestFilter {

    /**
     * 请求头齐全时绑定租户上下文并写入 MDC，请求结束后无条件清理。
     *
     * @param request     当前请求
     * @param response    当前响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String tenantCode = request.getHeader(TenantHeaders.TENANT_CODE);
        String protocolVersion = request.getHeader(TenantHeaders.PROTOCOL_VERSION);
        if (StringUtils.hasText(tenantCode) && StringUtils.hasText(protocolVersion)) {
            TenantContext.set(new TenantInfo(tenantCode, protocolVersion));
            MdcContext.put(MdcKeys.TENANT_CODE, tenantCode);
            MdcContext.put(MdcKeys.PROTOCOL_VERSION, protocolVersion);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MdcContext.remove(MdcKeys.TENANT_CODE);
            MdcContext.remove(MdcKeys.PROTOCOL_VERSION);
        }
    }
}
