package com.wangguangwu.claim.application.support.config;

import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置，保证 Redis 故障时应用仍可启动并走 MySQL 降级链路。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Configuration(proxyBeanMethods = false)
public class RedissonConfiguration {

    private static final int CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int COMMAND_TIMEOUT_MILLIS = 1000;
    private static final int COMMAND_RETRY_ATTEMPTS = 1;
    private static final int COMMAND_RETRY_INTERVAL_MILLIS = 200;

    /**
     * 延迟建立连接，并收紧失败判定时间，使 Redis 故障时尽快切到 MySQL 降级链路。
     *
     * @return Redisson 配置定制器
     */
    @Bean
    public RedissonAutoConfigurationCustomizer intakeRedissonCustomizer() {
        return config -> {
            config.setLazyInitialization(true);
            config.useSingleServer()
                    .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                    .setTimeout(COMMAND_TIMEOUT_MILLIS)
                    .setRetryAttempts(COMMAND_RETRY_ATTEMPTS)
                    .setRetryInterval(COMMAND_RETRY_INTERVAL_MILLIS);
        };
    }
}
