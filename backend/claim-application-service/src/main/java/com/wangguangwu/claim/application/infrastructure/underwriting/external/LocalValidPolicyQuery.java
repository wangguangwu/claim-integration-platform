package com.wangguangwu.claim.application.infrastructure.underwriting.external;

import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.underwriting.port.ValidPolicyQuery;
import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Component
public class LocalValidPolicyQuery implements ValidPolicyQuery {

    private static final InteractionType INTERACTION_TYPE = InteractionType.UNDERWRITING;
    private static final String CLAIM_APPLICATION_NO_PLACEHOLDER = "-";

    private final LocalIntegrationProperties properties;

    /**
     * 查询被保人是否存在有效保单，返回值由本地配置决定。
     *
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     * @return 存在有效保单时为 true
     */
    @Override
    public boolean hasValidPolicy(String tenantCode, String insuredCertificateNo) {
        pause();
        return properties.validPolicy();
    }

    private void pause() {
        Duration delay = properties.delay();
        log.info("外部系统本地实现调用，targetSystem={}，interactionType={}，claimApplicationNo={}，delay={}",
                INTERACTION_TYPE.targetSystem(), INTERACTION_TYPE, CLAIM_APPLICATION_NO_PLACEHOLDER, delay);
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("外部系统本地实现休眠被中断，targetSystem=" + INTERACTION_TYPE.targetSystem(),
                    exception);
        }
    }
}
