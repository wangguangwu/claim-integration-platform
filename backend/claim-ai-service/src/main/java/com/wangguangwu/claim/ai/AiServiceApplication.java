package com.wangguangwu.claim.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 异常诊断服务启动类。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@EnableFeignClients
@SpringBootApplication
public class AiServiceApplication {

    /**
     * 启动 AI 异常诊断服务并启用 Feign 客户端。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
