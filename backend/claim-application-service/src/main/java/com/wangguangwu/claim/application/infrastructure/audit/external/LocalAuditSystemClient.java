package com.wangguangwu.claim.application.infrastructure.audit.external;

import com.wangguangwu.claim.application.usecase.audit.model.AuditDeliveryRequest;
import com.wangguangwu.claim.application.usecase.audit.port.ServiceAuditSystemPort;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionType;
import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import com.wangguangwu.claim.application.infrastructure.integration.local.AbstractLocalIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审核系统交付端口的本地实现：按配置休眠并打印交互日志，开启失败开关时抛出异常。
 *
 * <p>抛出异常表示审核系统不可用，交付事务不会开启，消息重投后可重新执行。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
@Component
public class LocalAuditSystemClient extends AbstractLocalIntegration implements ServiceAuditSystemPort {

    /**
     * 装配本地实现，指定交互类型与配置。
     *
     * @param properties 本地集成配置
     */
    public LocalAuditSystemClient(LocalIntegrationProperties properties) {
        super(InteractionType.AUDIT_DELIVERY, properties);
    }

    /**
     * 交付申请给审核系统；本地实现不发起外部请求，失败开关开启时抛出异常。
     *
     * @param request 交付入参
     */
    @Override
    public void deliver(AuditDeliveryRequest request) {
        pause(request.claimApplicationNo());
        if (properties().failAuditDelivery()) {
            log.error("外部系统本地实现交付失败，targetSystem={}，claimApplicationNo={}", interactionType().targetSystem(),
                    request.claimApplicationNo());
            throw new IllegalStateException("审核系统本地实现交付失败，claimApplicationNo=" + request.claimApplicationNo());
        }
    }
}
