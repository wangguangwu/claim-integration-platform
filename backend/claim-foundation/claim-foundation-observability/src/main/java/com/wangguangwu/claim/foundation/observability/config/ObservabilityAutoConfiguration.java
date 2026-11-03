package com.wangguangwu.claim.foundation.observability.config;

import com.wangguangwu.claim.foundation.observability.flow.FlowLogAspect;
import com.wangguangwu.claim.foundation.observability.trace.TraceMdcFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 可观测性自动装配，注册流程日志切面与链路 MDC 过滤器，均按运行环境与依赖条件装配。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@AutoConfiguration
public class ObservabilityAutoConfiguration {

    /**
     * 注册流程日志切面，业务可声明自己的切面替换默认实现。
     *
     * @return 流程日志切面
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnMissingBean
    public FlowLogAspect flowLogAspect() {
        return new FlowLogAspect();
    }

    /**
     * 注册链路标识过滤器，优先于租户与业务过滤器执行。
     *
     * @return 过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    @ConditionalOnMissingBean(name = "traceMdcFilter")
    public FilterRegistrationBean<TraceMdcFilter> traceMdcFilter() {
        FilterRegistrationBean<TraceMdcFilter> registration = new FilterRegistrationBean<>(new TraceMdcFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
