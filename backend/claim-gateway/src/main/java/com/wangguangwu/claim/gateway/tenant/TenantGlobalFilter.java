package com.wangguangwu.claim.gateway.tenant;

import com.wangguangwu.claim.foundation.tenant.protocol.TenantHeaders;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 校验租户标识与协议版本，缺失直接拒绝，不进入业务服务。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@Component
public class TenantGlobalFilter implements GlobalFilter, Ordered {

    private static final String REJECT_BODY = "{\"code\":\"TENANT_HEADER_MISSING\",\"message\":\"缺少租户标识或协议版本\"}";

    /**
     * 租户头不全时写回 400 并终止链路，齐全时放行。
     *
     * @param exchange 当前请求上下文
     * @param chain    过滤器链
     * @return 处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String tenantCode = exchange.getRequest().getHeaders().getFirst(TenantHeaders.TENANT_CODE);
        String protocolVersion = exchange.getRequest().getHeaders().getFirst(TenantHeaders.PROTOCOL_VERSION);
        if (!StringUtils.hasText(tenantCode) || !StringUtils.hasText(protocolVersion)) {
            log.warn("拒绝缺少租户标识的请求: path={}", exchange.getRequest().getURI().getPath());
            return reject(exchange);
        }
        return chain.filter(exchange);
    }

    /**
     * 输出统一的租户缺失错误响应。
     *
     * @param exchange 当前请求上下文
     * @return 响应写出结果
     */
    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer body = response.bufferFactory().wrap(REJECT_BODY.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
