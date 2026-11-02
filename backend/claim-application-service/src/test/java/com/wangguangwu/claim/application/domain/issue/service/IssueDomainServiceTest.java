package com.wangguangwu.claim.application.domain.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.port.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.event.IssueClosedEvent;
import com.wangguangwu.claim.application.domain.issue.event.IssueDomainEventPublisher;
import com.wangguangwu.claim.application.domain.issue.event.IssueRaisedEvent;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 问题件领域服务单元测试：验证跨聚合业务规则、条件状态更新与领域事件发布。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("问题件领域服务")
class IssueDomainServiceTest {

    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";
    private static final String ISSUE_NO = "ISS202610310001";
    private static final String REASON = "缺少病历首页";
    private static final String AUDITOR = "auditor_zhangsan";
    private static final String ISSUE_CONTENT = "{\"missingMaterials\":[\"病历首页\"]}";

    private ClaimApplicationRepository claimApplicationRepository;
    private ClaimApplicationIssueRepository claimApplicationIssueRepository;
    private IssueDomainEventPublisher issueDomainEventPublisher;

    private IssueDomainService domainService;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        claimApplicationIssueRepository = mock(ClaimApplicationIssueRepository.class);
        issueDomainEventPublisher = mock(IssueDomainEventPublisher.class);

        domainService = new IssueDomainService(
                claimApplicationRepository,
                claimApplicationIssueRepository,
                issueDomainEventPublisher
        );
    }

    @Test
    @DisplayName("下发问题件：申请处于等待审核 → 创建问题件，以迁移前状态推进申请，发布事件")
    void raiseIssue_validApplication_createsIssueAndTransitionsStatus() {
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(claimApplicationIssueRepository.hasOpenIssue(CLAIM_APPLICATION_NO)).thenReturn(false);
        when(claimApplicationRepository.updateStatus(application, ClaimApplicationStatus.WAIT_AUDIT))
                .thenReturn(true);

        ClaimApplicationIssue result = domainService.raiseIssue(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);

        assertThat(result.getIssueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.getClaimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        verify(claimApplicationIssueRepository).insert(any(ClaimApplicationIssue.class));
        verify(claimApplicationRepository).updateStatus(application, ClaimApplicationStatus.WAIT_AUDIT);
        verify(issueDomainEventPublisher).publish(any(IssueRaisedEvent.class));
    }

    @Test
    @DisplayName("下发问题件：申请不存在 → 抛 ISSUE_APPLICATION_NOT_FOUND")
    void raiseIssue_applicationNotFound_throwsException() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> domainService.raiseIssue(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND)
                .hasMessageContaining(CLAIM_APPLICATION_NO);

        verify(claimApplicationIssueRepository, never()).insert(any());
        verify(issueDomainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("下发问题件：申请状态不是等待审核 → 抛 ISSUE_APPLICATION_STATUS_INVALID")
    void raiseIssue_invalidStatus_throwsException() {
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.PENDING_ACCEPT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> domainService.raiseIssue(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID)
                .hasMessageContaining(CLAIM_APPLICATION_NO)
                .hasMessageContaining("PENDING_ACCEPT");

        verify(claimApplicationIssueRepository, never()).insert(any());
        verify(issueDomainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("下发问题件：已有未关闭问题件 → 抛 ISSUE_ALREADY_OPEN")
    void raiseIssue_existingOpenIssue_throwsException() {
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(claimApplicationIssueRepository.hasOpenIssue(CLAIM_APPLICATION_NO)).thenReturn(true);

        assertThatThrownBy(() -> domainService.raiseIssue(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_ALREADY_OPEN)
                .hasMessageContaining(CLAIM_APPLICATION_NO);

        verify(claimApplicationIssueRepository, never()).insert(any());
        verify(issueDomainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("下发问题件：申请状态被并发改变 → 抛 ISSUE_APPLICATION_STATUS_INVALID 并回滚")
    void raiseIssue_statusUpdateConflict_throwsException() {
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(claimApplicationRepository.updateStatus(application, ClaimApplicationStatus.WAIT_AUDIT))
                .thenReturn(false);

        assertThatThrownBy(() -> domainService.raiseIssue(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID)
                .hasMessageContaining(CLAIM_APPLICATION_NO);

        verify(issueDomainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("关闭问题件：问题件未关闭 → 关闭问题件，以迁移前状态回退申请，发布事件")
    void closeIssue_openIssue_closesIssueAndTransitionsStatusBack() {
        ClaimApplicationIssue issue = mockIssue();
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.ISSUE);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(claimApplicationRepository.updateStatus(application, ClaimApplicationStatus.ISSUE))
                .thenReturn(true);

        ClaimApplicationIssue result = domainService.closeIssue(issue, AUDITOR);

        assertThat(result).isSameAs(issue);
        verify(issue).close(AUDITOR);
        verify(claimApplicationIssueRepository).update(issue);
        verify(claimApplicationRepository).updateStatus(application, ClaimApplicationStatus.ISSUE);
        verify(issueDomainEventPublisher).publish(any(IssueClosedEvent.class));
    }

    @Test
    @DisplayName("关闭问题件：关联申请不存在 → 抛 ISSUE_APPLICATION_NOT_FOUND")
    void closeIssue_applicationNotFound_throwsException() {
        ClaimApplicationIssue issue = mockIssue();
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> domainService.closeIssue(issue, AUDITOR))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND)
                .hasMessageContaining(ISSUE_NO)
                .hasMessageContaining(CLAIM_APPLICATION_NO);

        verify(issueDomainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("关闭问题件：申请状态被并发改变 → 抛 ISSUE_APPLICATION_STATUS_INVALID 并回滚")
    void closeIssue_statusUpdateConflict_throwsException() {
        ClaimApplicationIssue issue = mockIssue();
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.ISSUE);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(claimApplicationRepository.updateStatus(application, ClaimApplicationStatus.ISSUE))
                .thenReturn(false);

        assertThatThrownBy(() -> domainService.closeIssue(issue, AUDITOR))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID)
                .hasMessageContaining(CLAIM_APPLICATION_NO);

        verify(issueDomainEventPublisher, never()).publish(any());
    }

    private ClaimApplication mockApplicationInStatus(ClaimApplicationStatus status) {
        ClaimApplication application = mock(ClaimApplication.class);
        when(application.getStatus()).thenReturn(status);
        when(application.isAwaitingAudit()).thenReturn(status == ClaimApplicationStatus.WAIT_AUDIT);
        doAnswer(invocation -> {
            when(application.getStatus()).thenReturn(ClaimApplicationStatus.ISSUE);
            return null;
        }).when(application).enterIssue();
        doAnswer(invocation -> {
            when(application.getStatus()).thenReturn(ClaimApplicationStatus.WAIT_AUDIT);
            return null;
        }).when(application).resumeAudit();
        return application;
    }

    private ClaimApplicationIssue mockIssue() {
        ClaimApplicationIssue issue = mock(ClaimApplicationIssue.class);
        when(issue.getIssueNo()).thenReturn(ISSUE_NO);
        when(issue.getClaimApplicationNo()).thenReturn(CLAIM_APPLICATION_NO);
        return issue;
    }
}
