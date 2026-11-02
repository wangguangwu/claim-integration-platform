package com.wangguangwu.claim.application.usecase.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.port.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssueStatus;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.service.IssueDomainService;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueCloseCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueCloseResult;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 问题件关闭用例单元测试：验证幂等处理与领域服务委托。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("问题件关闭用例")
class ClaimApplicationIssueCloseUseCaseTest {

    private static final String ISSUE_NO = "ISS202610310001";
    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";
    private static final String AUDITOR = "auditor_lisi";

    private IssueDomainService issueDomainService;
    private ClaimApplicationIssueRepository claimApplicationIssueRepository;

    private ClaimApplicationIssueCloseUseCase useCase;

    @BeforeEach
    void setUp() {
        issueDomainService = mock(IssueDomainService.class);
        claimApplicationIssueRepository = mock(ClaimApplicationIssueRepository.class);

        useCase = new ClaimApplicationIssueCloseUseCase(issueDomainService, claimApplicationIssueRepository);
    }

    @Test
    @DisplayName("首次关闭：调用领域服务，返回 closed=true")
    void close_firstTime_callsDomainServiceAndReturnsClosed() {
        ClaimApplicationIssueCloseCommand command = new ClaimApplicationIssueCloseCommand(ISSUE_NO, AUDITOR);
        ClaimApplicationIssue issue = mockIssueInStatus(ClaimApplicationIssueStatus.OPEN);
        
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(issue));

        ClaimApplicationIssueCloseResult result = useCase.close(command);

        assertThat(result.issueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.closed()).isTrue();
        verify(issueDomainService).closeIssue(eq(issue), eq(AUDITOR));
    }

    @Test
    @DisplayName("幂等关闭：问题件已 CLOSED → 返回成功，不调用领域服务")
    void close_alreadyClosed_returnsIdempotentSuccessWithoutCallingDomainService() {
        ClaimApplicationIssueCloseCommand command = new ClaimApplicationIssueCloseCommand(ISSUE_NO, AUDITOR);
        ClaimApplicationIssue issue = mockIssueInStatus(ClaimApplicationIssueStatus.CLOSED);
        
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(issue));

        ClaimApplicationIssueCloseResult result = useCase.close(command);

        assertThat(result.issueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.closed()).isFalse();
        verify(issueDomainService, never()).closeIssue(any(), any());
    }

    @Test
    @DisplayName("问题件不存在：抛 ISSUE_NOT_FOUND")
    void close_issueNotFound_throwsException() {
        ClaimApplicationIssueCloseCommand command = new ClaimApplicationIssueCloseCommand(ISSUE_NO, AUDITOR);
        
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.close(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND)
                .hasMessageContaining(ISSUE_NO);
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
}
