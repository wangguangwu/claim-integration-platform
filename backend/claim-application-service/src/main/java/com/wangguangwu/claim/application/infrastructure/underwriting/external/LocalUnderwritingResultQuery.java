package com.wangguangwu.claim.application.infrastructure.underwriting.external;

import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResult;
import com.wangguangwu.claim.application.domain.underwriting.port.UnderwritingResultQuery;
import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import com.wangguangwu.claim.application.infrastructure.integration.local.AbstractLocalIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自动承保结果查询端口的本地实现：按配置休眠并返回 {@code underwriting-result}。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
@Component
public class LocalUnderwritingResultQuery extends AbstractLocalIntegration implements UnderwritingResultQuery {

    /**
     * 装配本地实现，指定交互类型与配置。
     *
     * @param properties 本地集成配置
     */
    public LocalUnderwritingResultQuery(LocalIntegrationProperties properties) {
        super(InteractionType.UNDERWRITING, properties);
    }

    /**
     * 按平台申请编号查询承保结果，返回值由本地配置决定。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 承保结果
     */
    @Override
    public UnderwritingResult query(String claimApplicationNo) {
        pause(claimApplicationNo);
        return properties().underwritingResult();
    }
}
