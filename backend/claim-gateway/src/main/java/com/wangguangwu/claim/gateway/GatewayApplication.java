package com.wangguangwu.claim.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@SpringBootApplication
public class GatewayApplication {

    /**
     * 启动网关服务，加载路由、全局过滤器和限流配置。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
