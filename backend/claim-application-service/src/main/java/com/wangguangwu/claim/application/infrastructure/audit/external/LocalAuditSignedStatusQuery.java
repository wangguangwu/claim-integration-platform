package com.wangguangwu.claim.application.infrastructure.audit.external;

import com.wangguangwu.claim.application.infrastructure.integration.config.LocalIntegrationProperties;
import com.wangguangwu.claim.application.infrastructure.integration.local.AbstractLocalIntegration;
import com.wangguangwu.claim.application.usecase.audit.model.AuditSignedStatus;
import com.wangguangwu.claim.application.usecase.audit.port.AuditSignedStatusQuery;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionType;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审核签收状态查询端口的本地实现：按配置返回已签收、处理中或未找到。
 *
 * <p>已签收但未配置案件号属于无效配置，抛出异常由补偿按查询失败处理，避免误完成申请。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@Component
public class LocalAuditSignedStatusQuery extends AbstractLocalIntegration implements AuditSignedStatusQuery {

    /**
     * 装配本地实现，指定交互类型与配置。
     *
     * @param properties 本地集成配置
     */
    public LocalAuditSignedStatusQuery(LocalIntegrationProperties properties) {
        super(InteractionType.AUDIT_SIGNED, properties);
    }

    /**
     * 查询审核签收状态；开启查询失败开关时抛出异常。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 审核签收状态
     */
    @Override
    public AuditSignedStatus query(String claimApplicationNo) {
        pause(claimApplicationNo);
        if (properties().failAuditSignedQuery()) {
            log.error("外部系统本地实现审核签收查询失败，targetSystem={}，claimApplicationNo={}",
                    interactionType().targetSystem(), claimApplicationNo);
            throw new IllegalStateException("审核系统本地实现查询失败，claimApplicationNo=" + claimApplicationNo);
        }
        return switch (properties().auditSignedResult()) {
            case SIGNED -> signedStatus(claimApplicationNo);
            case PROCESSING -> new AuditSignedStatus.Processing();
            case NOT_FOUND -> new AuditSignedStatus.NotFound();
        };
    }

    private AuditSignedStatus signedStatus(String claimApplicationNo) {
        List<String> caseNos = properties().auditSignedCaseNos();
        if (caseNos == null || caseNos.isEmpty()) {
            log.error("外部系统本地实现已签收但缺少案件号，targetSystem={}，claimApplicationNo={}",
                    interactionType().targetSystem(), claimApplicationNo);
            throw new IllegalStateException("审核系统本地实现已签收但缺少案件号，claimApplicationNo="
                    + claimApplicationNo);
        }
        return new AuditSignedStatus.Signed(caseNos);
    }
}
