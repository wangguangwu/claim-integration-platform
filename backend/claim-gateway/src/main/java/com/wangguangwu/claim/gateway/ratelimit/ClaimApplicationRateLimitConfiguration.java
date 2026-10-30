package com.wangguangwu.claim.gateway.ratelimit;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.wangguangwu.claim.foundation.tenant.protocol.TenantHeaders;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

/**
 * 网关限流装配：路由兜底规则 + 接口级租户配额，规则随启动从配置加载。
 *
 * <p>路由兜底统计 {@code /api/claim/**} 的全部请求，是本路由的总上界；
 * 接口级规则按接口名注册 Sentinel API 定义，再按租户编码精确匹配请求头生成参数规则。
 * 两类规则同时命中时取较小值，因此租户配额不得大于兜底阈值。
 * 同一租户在不同接口上的配额互不影响，签收超限不会消耗承保回调的额度。
 * 阈值口径是单节点 QPS，租户全局上界等于单节点阈值乘以网关节点数。</p>
 *
 * @author wangguangwu
 * @since 2026-10-30
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClaimApplicationRateLimitProperties.class)
@RequiredArgsConstructor
public class ClaimApplicationRateLimitConfiguration {

    /**
     * 限流资源名与签收路由 id 一致，只作用于该路由。
     */
    private static final String CLAIM_APPLICATION_ROUTE_ID = "claim-application-service";

    private static final int EVALUATION_INTERVAL_SECONDS = 1;

    /**
     * 排在租户校验之后，缺少租户头的请求先被 400 拒绝，不进入限流统计。
     */
    private static final int SENTINEL_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    private final ClaimApplicationRateLimitProperties properties;

    /**
     * 启动时注册接口定义与两层规则，并注册限流响应。
     *
     * <p>必须先注册 API 定义，接口级规则才能按名字找到资源。</p>
     */
    @PostConstruct
    void loadRateLimitRules() {
        int defaultQps = properties.defaultQps();
        if (defaultQps <= 0) {
            throw new IllegalStateException("网关限流路由兜底阈值必须为正数, default-qps=" + defaultQps);
        }
        Map<String, ClaimApplicationRateLimitProperties.RateLimitedApi> apis =
                Optional.ofNullable(properties.apis()).orElse(Map.of());
        GatewayApiDefinitionManager.loadApiDefinitions(apiDefinitions(apis));
        List<GatewayFlowRule> rules = new ArrayList<>();
        apis.forEach((apiName, api) -> rules.addAll(apiRules(apiName, api, defaultQps)));
        rules.add(routeDefaultRule(defaultQps));
        GatewayRuleManager.loadRules(new LinkedHashSet<>(rules));
        GatewayCallbackManager.setBlockHandler(new ClaimApplicationRateLimitBlockHandler());
        log.info("网关限流规则已加载: routeId={}, defaultQps={}, apiCount={}, ruleCount={}, apiTenantQps={}",
                CLAIM_APPLICATION_ROUTE_ID, defaultQps, apis.size(), rules.size(), apiTenantQps(apis));
    }

    /**
     * 注册 Sentinel 网关过滤器，限流判定在路由转发之前完成。
     *
     * <p>过滤器自身实现 {@link Ordered}，排序值必须由构造器给出。</p>
     *
     * @return 网关全局过滤器
     */
    @Bean
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter(SENTINEL_FILTER_ORDER);
    }

    /**
     * 注册限流异常处理链，把限流异常交给回调写出 429。
     *
     * @param viewResolvers         视图解析器
     * @param serverCodecConfigurer 服务端编解码配置
     * @return 限流异常处理器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler(
            List<ViewResolver> viewResolvers, ServerCodecConfigurer serverCodecConfigurer) {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    /**
     * 把配置的接口转换成 Sentinel API 定义，一个接口一条路径谓词。
     *
     * @param apis 接口配置，键是 API 定义名
     * @return API 定义集合
     */
    private Set<ApiDefinition> apiDefinitions(Map<String, ClaimApplicationRateLimitProperties.RateLimitedApi> apis) {
        Set<ApiDefinition> definitions = new LinkedHashSet<>();
        apis.forEach((apiName, api) -> {
            validateApi(apiName, api);
            ApiPathPredicateItem predicateItem = new ApiPathPredicateItem()
                    .setMatchStrategy(matchStrategy(api).sentinelMatchStrategy())
                    .setPattern(api.path());
            definitions.add(new ApiDefinition(apiName)
                    .setPredicateItems(new LinkedHashSet<>(List.of(predicateItem))));
        });
        return definitions;
    }

    /**
     * 生成一个接口下各租户的参数规则。
     *
     * <p>参数下标由规则管理器按资源统一编号，这里不设置。</p>
     *
     * @param apiName     API 定义名
     * @param api         接口配置
     * @param defaultQps  路由兜底阈值，租户配额不得超过它
     * @return 限流规则
     */
    private List<GatewayFlowRule> apiRules(String apiName, ClaimApplicationRateLimitProperties.RateLimitedApi api,
                                           int defaultQps) {
        List<GatewayFlowRule> rules = new ArrayList<>();
        api.tenants().forEach((tenantCode, qps) -> {
            if (qps == null || qps <= 0 || qps > defaultQps) {
                throw new IllegalStateException("接口租户限流阈值必须在 1 与路由兜底阈值之间, apiName="
                        + apiName + ", tenantCode=" + tenantCode + ", qps=" + qps + ", default-qps=" + defaultQps);
            }
            rules.add(new GatewayFlowRule(apiName)
                    .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                    .setCount(qps)
                    .setIntervalSec(EVALUATION_INTERVAL_SECONDS)
                    .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT)
                    .setParamItem(new GatewayParamFlowItem()
                            .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                            .setFieldName(TenantHeaders.TENANT_CODE)
                            .setMatchStrategy(SentinelGatewayConstants.PARAM_MATCH_STRATEGY_EXACT)
                            .setPattern(tenantCode)));
        });
        return rules;
    }

    /**
     * 生成未配置接口配额时的兜底规则，统计该路由的全部请求。
     *
     * @param qps 单节点每秒请求阈值
     * @return 限流规则
     */
    private GatewayFlowRule routeDefaultRule(int qps) {
        return new GatewayFlowRule(CLAIM_APPLICATION_ROUTE_ID)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(qps)
                .setIntervalSec(EVALUATION_INTERVAL_SECONDS)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
    }

    /**
     * 校验接口配置，配置错误在启动阶段暴露，不等到线上限流失效。
     *
     * @param apiName API 定义名
     * @param api     接口配置
     */
    private void validateApi(String apiName, ClaimApplicationRateLimitProperties.RateLimitedApi api) {
        if (apiName == null || apiName.isBlank()) {
            throw new IllegalStateException("接口限流配置缺少 API 定义名");
        }
        if (api == null || api.path() == null || !api.path().startsWith("/")) {
            throw new IllegalStateException("接口限流路径必须以 / 开头, apiName=" + apiName);
        }
        if (api.tenants() == null || api.tenants().isEmpty()) {
            throw new IllegalStateException("接口限流至少配置一个租户配额, apiName=" + apiName);
        }
    }

    /**
     * 取接口的路径匹配策略，未配置时按精确匹配。
     *
     * @param api 接口配置
     * @return 路径匹配策略
     */
    private ApiPathMatchStrategy matchStrategy(ClaimApplicationRateLimitProperties.RateLimitedApi api) {
        return api.matchStrategy() == null ? ApiPathMatchStrategy.EXACT : api.matchStrategy();
    }

    /**
     * 汇总各接口的租户配额，用于启动日志核对生效配置。
     *
     * @param apis 接口配置
     * @return 接口名到租户阈值的有序映射
     */
    private Map<String, Map<String, Integer>> apiTenantQps(
            Map<String, ClaimApplicationRateLimitProperties.RateLimitedApi> apis) {
        Map<String, Map<String, Integer>> tenantQps = new LinkedHashMap<>();
        apis.forEach((apiName, api) -> tenantQps.put(apiName, api.tenants()));
        return tenantQps;
    }
}
