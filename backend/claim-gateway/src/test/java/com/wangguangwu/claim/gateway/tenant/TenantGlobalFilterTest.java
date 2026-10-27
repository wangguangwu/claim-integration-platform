package com.wangguangwu.claim.gateway.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wangguangwu.claim.foundation.tenant.protocol.TenantHeaders;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关租户校验过滤器测试：正常放行、缺失拒绝、重复请求结果一致。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class TenantGlobalFilterTest {

    private static final String REQUEST_PATH = "/api/case/x";
    private static final String TENANT_CODE = "tenant-a";
    private static final String PROTOCOL_VERSION = "v1";
    private static final String MISSING_HEADER_CODE = "TENANT_HEADER_MISSING";

    private final TenantGlobalFilter tenantGlobalFilter = new TenantGlobalFilter();

    @Test
    void shouldPassThroughWhenTenantHeadersPresent() {
        AtomicBoolean chained = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_PATH)
                .header(TenantHeaders.TENANT_CODE, TENANT_CODE)
                .header(TenantHeaders.PROTOCOL_VERSION, PROTOCOL_VERSION)
                .build());

        tenantGlobalFilter.filter(exchange, chainMarking(chained)).block();

        assertTrue(chained.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldRejectWhenTenantCodeMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_PATH)
                .header(TenantHeaders.PROTOCOL_VERSION, PROTOCOL_VERSION)
                .build());

        assertRejected(exchange);
    }

    @Test
    void shouldRejectWhenProtocolVersionMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_PATH)
                .header(TenantHeaders.TENANT_CODE, TENANT_CODE)
                .build());

        assertRejected(exchange);
    }

    @Test
    void shouldRejectIdenticallyOnRepeatedRequest() {
        MockServerWebExchange first = MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_PATH).build());
        MockServerWebExchange second = MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_PATH).build());

        assertEquals(rejectedBody(first), rejectedBody(second));
    }

    private void assertRejected(MockServerWebExchange exchange) {
        assertTrue(rejectedBody(exchange).contains(MISSING_HEADER_CODE));
    }

    private String rejectedBody(MockServerWebExchange exchange) {
        tenantGlobalFilter.filter(exchange, chainMarking(new AtomicBoolean(false))).block();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        return body;
    }

    private GatewayFilterChain chainMarking(AtomicBoolean chained) {
        return exchange -> {
            chained.set(true);
            return Mono.empty();
        };
    }
}
