package com.wangguangwu.claim.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关 TraceId 过滤器测试：缺失时生成、存在时透传。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class TraceIdGlobalFilterTest {

    private static final String REQUEST_PATH = "/api/application/x";
    private static final String UPSTREAM_TRACE_ID = "trace-001";
    private static final int GENERATED_TRACE_ID_LENGTH = 32;

    private final TraceIdGlobalFilter traceIdGlobalFilter = new TraceIdGlobalFilter();

    @Test
    void shouldGenerateTraceIdWhenAbsent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_PATH).build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        traceIdGlobalFilter.filter(exchange, forwardTo(forwarded)).block();

        String traceId = forwarded.get().getRequest().getHeaders().getFirst(TraceHeaders.TRACE_ID);
        assertNotNull(traceId);
        assertEquals(GENERATED_TRACE_ID_LENGTH, traceId.length());
        assertEquals(traceId, exchange.getResponse().getHeaders().getFirst(TraceHeaders.TRACE_ID));
    }

    @Test
    void shouldReuseUpstreamTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(REQUEST_PATH)
                .header(TraceHeaders.TRACE_ID, UPSTREAM_TRACE_ID)
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        traceIdGlobalFilter.filter(exchange, forwardTo(forwarded)).block();

        assertEquals(UPSTREAM_TRACE_ID,
                forwarded.get().getRequest().getHeaders().getFirst(TraceHeaders.TRACE_ID));
        assertEquals(UPSTREAM_TRACE_ID, exchange.getResponse().getHeaders().getFirst(TraceHeaders.TRACE_ID));
    }

    private GatewayFilterChain forwardTo(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }
}
