package com.wangguangwu.claim.application.infrastructure.underwriting.external;

import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResult;
import com.wangguangwu.claim.application.domain.underwriting.port.UnderwritingResultQuery;
import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自动承保结果查询端口的本地实现：按配置休眠并返回 {@code underwriting-result}。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LocalUnderwritingResultQuery implements UnderwritingResultQuery {

    private static final InteractionType INTERACTION_TYPE = InteractionType.UNDERWRITING;

    private final LocalIntegrationProperties properties;

    /**
     * 按平台申请编号查询承保结果，返回值由本地配置决定。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 承保结果
     */
    @Override
    public UnderwritingResult query(String claimApplicationNo) {
        pause(claimApplicationNo);
        return properties.underwritingResult();
    }

    private void pause(String claimApplicationNo) {
        Duration delay = properties.delay();
        log.info("外部系统本地实现调用，targetSystem={}，interactionType={}，claimApplicationNo={}，delay={}",
                INTERACTION_TYPE.targetSystem(), INTERACTION_TYPE, claimApplicationNo, delay);
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("外部系统本地实现休眠被中断，claimApplicationNo=" + claimApplicationNo,
                    exception);
        }
    }
}
