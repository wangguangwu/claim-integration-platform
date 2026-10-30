package com.wangguangwu.claim.gateway.ratelimit;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 租户限流触发后的响应，结构与网关现有拒绝体保持一致。
 *
 * @author wangguangwu
 * @since 2026-10-30
 */
public class ClaimApplicationRateLimitBlockHandler implements BlockRequestHandler {

    private static final String RATE_LIMITED_BODY =
            "{\"code\":\"CLAIM_APPLICATION_RATE_LIMITED\",\"message\":\"租户请求超过限流阈值\"}";

    /**
     * 返回 429 与限流错误码，请求不再进入业务服务。
     *
     * @param exchange  当前请求上下文
     * @param throwable 触发限流的异常
     * @return 限流响应
     */
    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable throwable) {
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(RATE_LIMITED_BODY);
    }
}
