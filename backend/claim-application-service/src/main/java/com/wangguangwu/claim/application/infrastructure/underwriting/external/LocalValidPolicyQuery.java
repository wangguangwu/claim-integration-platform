package com.wangguangwu.claim.application.infrastructure.underwriting.external;

import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionType;
import com.wangguangwu.claim.application.usecase.underwriting.port.ValidPolicyQuery;
import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import com.wangguangwu.claim.application.infrastructure.integration.local.AbstractLocalIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 有效保单查询端口的本地实现：按配置休眠并返回 {@code valid-policy}。
 *
 * <p>端口只接收租户编码与证件号，日志以占位符表示申请编号，不打印证件号。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
@Component
public class LocalValidPolicyQuery extends AbstractLocalIntegration implements ValidPolicyQuery {

    /**
     * 装配本地实现，指定交互类型与配置。
     *
     * @param properties 本地集成配置
     */
    public LocalValidPolicyQuery(LocalIntegrationProperties properties) {
        super(InteractionType.UNDERWRITING, properties);
    }

    /**
     * 查询被保人是否存在有效保单，返回值由本地配置决定。
     *
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     * @return 存在有效保单时为 true
     */
    @Override
    public boolean hasValidPolicy(String tenantCode, String insuredCertificateNo) {
        pause(CLAIM_APPLICATION_NO_PLACEHOLDER);
        return properties().validPolicy();
    }
}
