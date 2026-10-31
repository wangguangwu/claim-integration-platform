package com.wangguangwu.claim.application.usecase.issue;

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
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssueStatus;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueException;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueCloseCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueCloseResult;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 问题件关闭用例单元测试：验证关闭与幂等行为。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("问题件关闭用例")
class ClaimApplicationIssueCloseUseCaseTest {

    private static final String ISSUE_NO = "ISS202610310001";
    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";
    private static final String AUDITOR = "auditor_lisi";

    private ClaimApplicationIssueRepository claimApplicationIssueRepository;
    private ClaimApplicationRepository claimApplicationRepository;

    private ClaimApplicationIssueCloseUseCase useCase;

    @BeforeEach
    void setUp() {
        claimApplicationIssueRepository = mock(ClaimApplicationIssueRepository.class);
        claimApplicationRepository = mock(ClaimApplicationRepository.class);

        useCase = new ClaimApplicationIssueCloseUseCase(claimApplicationIssueRepository, claimApplicationRepository);
    }

    @Test
    @DisplayName("首次关闭：问题件状态为 OPEN → 关闭成功，申请状态回退 WAIT_AUDIT")
    void close_firstTime_closesIssueAndTransitionsStatusBack() {
        ClaimApplicationIssueCloseCommand command = new ClaimApplicationIssueCloseCommand(ISSUE_NO, AUDITOR);
        ClaimApplicationIssue issue = mockIssueInStatus(ClaimApplicationIssueStatus.OPEN);
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.ISSUE);
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(issue));
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        
        // Mock transitionToWaitAudit() to change the status
        org.mockito.Mockito.doAnswer(invocation -> {
            when(application.getStatus()).thenReturn(ClaimApplicationStatus.WAIT_AUDIT);
            return null;
        }).when(application).transitionToWaitAudit();

        ClaimApplicationIssueCloseResult result = useCase.close(command);

        assertThat(result.issueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.closed()).isTrue();
        verify(claimApplicationIssueRepository).update(issue);
        verify(claimApplicationRepository).updateStatus(application, ClaimApplicationStatus.WAIT_AUDIT);
    }

    @Test
    @DisplayName("幂等关闭：问题件已 CLOSED → 返回成功，不重复处理")
    void close_alreadyClosed_returnsIdempotentSuccess() {
        ClaimApplicationIssueCloseCommand command = new ClaimApplicationIssueCloseCommand(ISSUE_NO, AUDITOR);
        ClaimApplicationIssue issue = mockIssueInStatus(ClaimApplicationIssueStatus.CLOSED);
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(issue));

        ClaimApplicationIssueCloseResult result = useCase.close(command);

        assertThat(result.issueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.closed()).isFalse();
        verify(claimApplicationIssueRepository, never()).update(any());
        verify(claimApplicationRepository, never()).findByClaimApplicationNo(any());
        verify(claimApplicationRepository, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("问题件不存在：抛 ISSUE_NOT_FOUND")
    void close_issueNotFound_throwsException() {
        ClaimApplicationIssueCloseCommand command = new ClaimApplicationIssueCloseCommand(ISSUE_NO, AUDITOR);
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.close(command))
                .isInstanceOf(ClaimApplicationIssueException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND)
                .hasMessageContaining(ISSUE_NO);
    }

    @Test
    @DisplayName("关联申请不存在：抛 ISSUE_APPLICATION_NOT_FOUND")
    void close_applicationNotFound_throwsException() {
        ClaimApplicationIssueCloseCommand command = new ClaimApplicationIssueCloseCommand(ISSUE_NO, AUDITOR);
        ClaimApplicationIssue issue = mockIssueInStatus(ClaimApplicationIssueStatus.OPEN);
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(issue));
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.close(command))
                .isInstanceOf(ClaimApplicationIssueException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND)
                .hasMessageContaining(ISSUE_NO)
                .hasMessageContaining(CLAIM_APPLICATION_NO);
    }

    private ClaimApplicationIssue mockIssueInStatus(ClaimApplicationIssueStatus status) {
        ClaimApplicationIssue issue = mock(ClaimApplicationIssue.class);
        when(issue.getIssueNo()).thenReturn(ISSUE_NO);
        when(issue.getClaimApplicationNo()).thenReturn(CLAIM_APPLICATION_NO);
        when(issue.getStatus()).thenReturn(status);
        when(issue.isOpen()).thenReturn(status == ClaimApplicationIssueStatus.OPEN);
        when(issue.isClosed()).thenReturn(status == ClaimApplicationIssueStatus.CLOSED);
        if (status == ClaimApplicationIssueStatus.CLOSED) {
            when(issue.getClosedAt()).thenReturn(LocalDateTime.now());
        }
        return issue;
    }

    private ClaimApplication mockApplicationInStatus(ClaimApplicationStatus status) {
        ClaimApplication application = mock(ClaimApplication.class);
        when(application.getClaimApplicationNo()).thenReturn(CLAIM_APPLICATION_NO);
        when(application.getStatus()).thenReturn(status);
        return application;
    }
}
