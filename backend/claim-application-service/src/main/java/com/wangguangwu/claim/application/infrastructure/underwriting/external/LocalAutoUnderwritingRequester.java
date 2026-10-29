package com.wangguangwu.claim.application.infrastructure.underwriting.external;

import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.underwriting.port.AutoUnderwritingRequester;
import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自动承保发起端口的本地实现：按配置休眠并打印交互日志，不发起外部请求。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LocalAutoUnderwritingRequester implements AutoUnderwritingRequester {

    private static final InteractionType INTERACTION_TYPE = InteractionType.UNDERWRITING;

    private final LocalIntegrationProperties properties;

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
