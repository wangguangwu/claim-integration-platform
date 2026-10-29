package com.wangguangwu.claim.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 理赔申请业务域启动类。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ClaimApplicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimApplicationServiceApplication.class, args);
    }
}
