package com.wangguangwu.claim.application.infrastructure.underwriting.external;

import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.underwriting.port.AutoUnderwritingRequester;
import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import com.wangguangwu.claim.application.infrastructure.integration.local.AbstractLocalIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自动承保发起端口的本地实现：按配置休眠并打印交互日志，不发起外部请求。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
@Component
public class LocalAutoUnderwritingRequester extends AbstractLocalIntegration implements AutoUnderwritingRequester {

    /**
     * 装配本地实现，指定交互类型与配置。
     *
     * @param properties 本地集成配置
     */
    public LocalAutoUnderwritingRequester(LocalIntegrationProperties properties) {
        super(InteractionType.UNDERWRITING, properties);
    }

    /**
     * 按平台申请编号发起自动承保；本地实现只记录调用，承保结果由结果查询端口按配置返回。
     *
     * @param claimApplicationNo        平台申请编号
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     */
    @Override
    public void request(String claimApplicationNo, String tenantCode, String insuredCertificateNo) {
        pause(claimApplicationNo);
    }
}
