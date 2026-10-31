package com.wangguangwu.claim.application.domain.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.event.IssueClosedEvent;
import com.wangguangwu.claim.application.domain.issue.event.IssueRaisedEvent;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 问题件领域服务单元测试：验证跨聚合业务规则与领域事件发布。
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
    private ApplicationEventPublisher eventPublisher;

    private IssueDomainService domainService;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        claimApplicationIssueRepository = mock(ClaimApplicationIssueRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        domainService = new IssueDomainService(
                claimApplicationRepository,
                claimApplicationIssueRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("下发问题件：申请状态为 WAIT_AUDIT → 创建问题件，状态变更为 ISSUE，发布事件")
    void raiseIssue_validApplication_createsIssueAndTransitionsStatus() {
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(claimApplicationIssueRepository.hasOpenIssue(CLAIM_APPLICATION_NO)).thenReturn(false);

        ClaimApplicationIssue result = domainService.raiseIssue(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);

        assertThat(result.getIssueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.getClaimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        verify(claimApplicationIssueRepository).insert(any(ClaimApplicationIssue.class));
        verify(claimApplicationRepository).updateStatus(application, ClaimApplicationStatus.ISSUE);
        verify(eventPublisher).publishEvent(any(IssueRaisedEvent.class));
    }

    @Test
    @DisplayName("下发问题件：申请不存在 → 抛 ISSUE_APPLICATION_NOT_FOUND")
    void raiseIssue_applicationNotFound_throwsException() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> domainService.raiseIssue(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT))
                .isInstanceOf(ClaimApplicationIssueException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND)
                .hasMessageContaining(CLAIM_APPLICATION_NO);

        verify(claimApplicationIssueRepository, never()).insert(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("下发问题件：申请状态不是 WAIT_AUDIT → 抛 ISSUE_APPLICATION_STATUS_INVALID")
    void raiseIssue_invalidStatus_throwsException() {
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.PENDING_ACCEPT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> domainService.raiseIssue(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT))
                .isInstanceOf(ClaimApplicationIssueException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID)
                .hasMessageContaining(CLAIM_APPLICATION_NO)
                .hasMessageContaining("PENDING_ACCEPT");

        verify(claimApplicationIssueRepository, never()).insert(any());
        verify(eventPublisher, never()).publishEvent(any());
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
                .isInstanceOf(ClaimApplicationIssueException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_ALREADY_OPEN)
                .hasMessageContaining(CLAIM_APPLICATION_NO);

        verify(claimApplicationIssueRepository, never()).insert(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("关闭问题件：问题件状态为 OPEN → 关闭问题件，申请状态回退，发布事件")
    void closeIssue_openIssue_closesIssueAndTransitionsStatusBack() {
        ClaimApplicationIssue issue = mockIssueInStatus(false);
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.ISSUE);
        
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(issue));
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));

        ClaimApplicationIssue result = domainService.closeIssue(ISSUE_NO, AUDITOR);

        assertThat(result.getIssueNo()).isEqualTo(ISSUE_NO);
        verify(claimApplicationIssueRepository).update(issue);
        verify(claimApplicationRepository).updateStatus(application, ClaimApplicationStatus.WAIT_AUDIT);
        verify(eventPublisher).publishEvent(any(IssueClosedEvent.class));
    }

    @Test
    @DisplayName("关闭问题件：问题件不存在 → 抛 ISSUE_NOT_FOUND")
    void closeIssue_issueNotFound_throwsException() {
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> domainService.closeIssue(ISSUE_NO, AUDITOR))
                .isInstanceOf(ClaimApplicationIssueException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND)
                .hasMessageContaining(ISSUE_NO);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("关闭问题件：问题件已关闭 → 幂等返回，不重复处理")
    void closeIssue_alreadyClosed_returnsIdempotently() {
        ClaimApplicationIssue issue = mockIssueInStatus(true);
        
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(issue));

        ClaimApplicationIssue result = domainService.closeIssue(ISSUE_NO, AUDITOR);

        assertThat(result.getIssueNo()).isEqualTo(ISSUE_NO);
        verify(claimApplicationIssueRepository, never()).update(any());
        verify(claimApplicationRepository, never()).findByClaimApplicationNo(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("关闭问题件：关联申请不存在 → 抛 ISSUE_APPLICATION_NOT_FOUND")
    void closeIssue_applicationNotFound_throwsException() {
        ClaimApplicationIssue issue = mockIssueInStatus(false);
        
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(issue));
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> domainService.closeIssue(ISSUE_NO, AUDITOR))
                .isInstanceOf(ClaimApplicationIssueException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND)
                .hasMessageContaining(ISSUE_NO)
                .hasMessageContaining(CLAIM_APPLICATION_NO);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private ClaimApplication mockApplicationInStatus(ClaimApplicationStatus status) {
        ClaimApplication application = mock(ClaimApplication.class);
        when(application.getStatus()).thenReturn(status);
        when(application.isWaitAudit()).thenReturn(status == ClaimApplicationStatus.WAIT_AUDIT);
        
        org.mockito.Mockito.doAnswer(invocation -> {
            when(application.getStatus()).thenReturn(ClaimApplicationStatus.ISSUE);
            return null;
        }).when(application).transitionToIssue();
        
        org.mockito.Mockito.doAnswer(invocation -> {
            when(application.getStatus()).thenReturn(ClaimApplicationStatus.WAIT_AUDIT);
            return null;
        }).when(application).transitionToWaitAudit();
        
        return application;
    }

    private ClaimApplicationIssue mockIssueInStatus(boolean closed) {
        ClaimApplicationIssue issue = mock(ClaimApplicationIssue.class);
        when(issue.getIssueNo()).thenReturn(ISSUE_NO);
        when(issue.getClaimApplicationNo()).thenReturn(CLAIM_APPLICATION_NO);
        when(issue.isOpen()).thenReturn(!closed);
        when(issue.isClosed()).thenReturn(closed);
        
        if (!closed) {
            org.mockito.Mockito.doAnswer(invocation -> {
                when(issue.isClosed()).thenReturn(true);
                when(issue.isOpen()).thenReturn(false);
                return null;
            }).when(issue).close(any());
        }
        
        return issue;
    }
}
