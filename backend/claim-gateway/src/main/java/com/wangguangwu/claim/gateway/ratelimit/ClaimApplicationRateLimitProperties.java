package com.wangguangwu.claim.gateway.ratelimit;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关限流配置：路由兜底阈值与各接口的租户配额。
 *
 * <p>阈值口径是单节点 QPS：每个阈值的全局上界等于单节点阈值乘以网关节点数。
 * 角色分工见 {@link ClaimApplicationRateLimitConfiguration}。</p>
 *
 * @param defaultQps 路由兜底阈值，统计该路由下全部请求，同时是接口配额的上限
 * @param apis       接口级租户配额，键是 Sentinel API 定义名
 * @author wangguangwu
 * @since 2026-10-30
 */
@ConfigurationProperties(prefix = "claim.gateway.rate-limit")
public record ClaimApplicationRateLimitProperties(int defaultQps, Map<String, RateLimitedApi> apis) {

    /**
     * 一个受限接口及其各租户配额。
     *
     * @param path          接口路径
     * @param matchStrategy 路径匹配策略
     * @param tenants       按租户编码覆盖的单节点阈值
     * @author wangguangwu
     * @since 2026-10-30
     */
    public record RateLimitedApi(String path, ApiPathMatchStrategy matchStrategy, Map<String, Integer> tenants) {
    }
}
