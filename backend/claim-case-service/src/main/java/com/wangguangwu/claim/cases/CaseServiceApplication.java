package com.wangguangwu.claim.cases;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 案件业务域启动类。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@SpringBootApplication
public class CaseServiceApplication {

    /**
     * 启动案件业务服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CaseServiceApplication.class, args);
    }
}
