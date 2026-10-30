package com.wangguangwu.claim.gateway.ratelimit;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;

/**
 * 接口路径匹配策略，取值与 Sentinel 网关 API 定义的路径匹配常量一一对应。
 *
 * @author wangguangwu
 * @since 2026-10-30
 */
public enum ApiPathMatchStrategy {

    /**
     * 请求路径与配置完全一致。
     */
    EXACT(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT),

    /**
     * 请求路径以配置为前缀。
     */
    PREFIX(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX),

    /**
     * 请求路径匹配配置的正则。
     */
    REGEX(SentinelGatewayConstants.URL_MATCH_STRATEGY_REGEX);

    private final int sentinelMatchStrategy;

    ApiPathMatchStrategy(int sentinelMatchStrategy) {
        this.sentinelMatchStrategy = sentinelMatchStrategy;
    }

    /**
     * 对应的 Sentinel 路径匹配常量。
     *
     * @return 路径匹配常量
     */
    public int sentinelMatchStrategy() {
        return sentinelMatchStrategy;
    }
}
