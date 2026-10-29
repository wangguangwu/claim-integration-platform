package com.wangguangwu.claim.application.infrastructure.integration.config;

import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResult;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 外部系统本地实现的配置，用于在联调时控制调用耗时与返回结果。
 *
 * <p>本地实现是进程内端口实现，不发起 HTTP 调用；真实外部系统接入时由对应适配器替换。</p>
 *
 * @param delay             每次本地调用的模拟耗时
 * @param validPolicy       保单查询端口是否返回存在有效保单
 * @param underwritingResult 承保结果查询端口的返回值
 * @param failMaterialTypes 命中这些材料类型时文件上传端口返回拒绝
 * @param failAuditDelivery 开启后审核交付端口固定抛出异常
 * @author wangguangwu
 * @since 2026-10-29
 */
@ConfigurationProperties(prefix = "claim.integration.local")
public record LocalIntegrationProperties(
        Duration delay,
        boolean validPolicy,
        UnderwritingResult underwritingResult,
        List<String> failMaterialTypes,
        boolean failAuditDelivery) {
}
