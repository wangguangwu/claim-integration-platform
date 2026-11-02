package com.wangguangwu.claim.application.infrastructure.issue.config;

import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.issue.event.IssueDomainEventPublisher;
import com.wangguangwu.claim.application.domain.issue.port.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.service.IssueDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 问题件领域服务的装配入口，领域服务本身保持零框架依赖。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Configuration
public class IssueDomainServiceConfiguration {

    /**
     * 装配问题件领域服务，依赖全部来自领域端口。
     *
     * @param claimApplicationRepository      申请聚合仓储
     * @param claimApplicationIssueRepository 问题件聚合仓储
     * @param issueDomainEventPublisher       领域事件发布端口
     * @return 问题件领域服务
     */
    @Bean
    public IssueDomainService issueDomainService(ClaimApplicationRepository claimApplicationRepository,
                                                 ClaimApplicationIssueRepository claimApplicationIssueRepository,
                                                 IssueDomainEventPublisher issueDomainEventPublisher) {
        return new IssueDomainService(
                claimApplicationRepository,
                claimApplicationIssueRepository,
                issueDomainEventPublisher);
    }
}
