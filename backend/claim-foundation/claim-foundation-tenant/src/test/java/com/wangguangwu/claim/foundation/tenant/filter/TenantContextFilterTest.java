package com.wangguangwu.claim.foundation.tenant.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wangguangwu.claim.foundation.tenant.context.TenantContext;
import com.wangguangwu.claim.foundation.tenant.protocol.TenantHeaders;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 租户上下文过滤器测试：请求内可读、请求后清理、异常路径不残留。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class TenantContextFilterTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String PROTOCOL_VERSION = "v1";

    private final TenantContextFilter tenantContextFilter = new TenantContextFilter();

    @Test
    void shouldBindTenantContextWithinRequest() throws Exception {
        MockHttpServletRequest request = tenantRequest();

        tenantContextFilter.doFilter(request, new MockHttpServletResponse(),
                (filteredRequest, filteredResponse) -> assertEquals(TENANT_CODE, TenantContext.tenantCode()));

        assertNull(TenantContext.get());
    }

    @Test
    void shouldLeaveContextEmptyWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        tenantContextFilter.doFilter(request, new MockHttpServletResponse(),
                (filteredRequest, filteredResponse) -> assertNull(TenantContext.get()));

        assertNull(TenantContext.get());
    }

    @Test
    void shouldClearContextWhenDownstreamFails() {
        MockHttpServletRequest request = tenantRequest();

        assertThrows(ServletException.class, () -> tenantContextFilter.doFilter(request,
                new MockHttpServletResponse(), (filteredRequest, filteredResponse) -> {
                    throw new ServletException("下游异常");
                }));

        assertNull(TenantContext.get());
    }

    private MockHttpServletRequest tenantRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantHeaders.TENANT_CODE, TENANT_CODE);
        request.addHeader(TenantHeaders.PROTOCOL_VERSION, PROTOCOL_VERSION);
        return request;
    }
}
