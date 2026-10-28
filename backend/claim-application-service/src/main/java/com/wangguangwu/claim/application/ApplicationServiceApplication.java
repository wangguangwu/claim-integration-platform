package com.wangguangwu.claim.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application 业务域启动类。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableScheduling
public class ApplicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationServiceApplication.class, args);
    }
}
