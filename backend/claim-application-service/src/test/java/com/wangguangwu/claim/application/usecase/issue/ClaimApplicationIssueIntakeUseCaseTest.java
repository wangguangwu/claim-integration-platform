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
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueException;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueIntakeCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueIntakeResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 问题件下发用例单元测试：验证下发与幂等行为。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("问题件下发用例")
class ClaimApplicationIssueIntakeUseCaseTest {

    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";
    private static final String ISSUE_NO = "ISS202610310001";
    private static final String REASON = "缺少病历首页";
    private static final String AUDITOR = "auditor_zhangsan";
    private static final String ISSUE_CONTENT = "{\"missingMaterials\":[\"病历首页\"]}";

    private ClaimApplicationRepository claimApplicationRepository;
    private ClaimApplicationIssueRepository claimApplicationIssueRepository;

    private ClaimApplicationIssueIntakeUseCase useCase;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        claimApplicationIssueRepository = mock(ClaimApplicationIssueRepository.class);

        useCase = new ClaimApplicationIssueIntakeUseCase(claimApplicationRepository, claimApplicationIssueRepository);
    }

    @Test
    @DisplayName("首次下发：申请状态为 WAIT_AUDIT → 创建成功，状态变更为 ISSUE")
    void receive_firstTime_createsIssueAndTransitionsStatus() {
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());
        when(claimApplicationIssueRepository.hasOpenIssue(CLAIM_APPLICATION_NO)).thenReturn(false);
        
        // Mock transitionToIssue() to change the status
        org.mockito.Mockito.doAnswer(invocation -> {
            when(application.getStatus()).thenReturn(ClaimApplicationStatus.ISSUE);
            return null;
        }).when(application).transitionToIssue();

        ClaimApplicationIssueIntakeResult result = useCase.receive(command);

        assertThat(result.issueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(result.created()).isTrue();
        verify(claimApplicationIssueRepository).insert(any(ClaimApplicationIssue.class));
        verify(claimApplicationRepository).updateStatus(application, ClaimApplicationStatus.ISSUE);
    }

    @Test
    @DisplayName("幂等下发：issueNo 已存在 → 返回成功，不重复创建")
    void receive_duplicateIssueNo_returnsIdempotentSuccess() {
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);
        ClaimApplicationIssue existingIssue = mock(ClaimApplicationIssue.class);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(existingIssue));

        ClaimApplicationIssueIntakeResult result = useCase.receive(command);

        assertThat(result.issueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(result.created()).isFalse();
        verify(claimApplicationIssueRepository, never()).insert(any());
        verify(claimApplicationRepository, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("并发下发：触发唯一约束 → 按幂等处理")
    void receive_concurrentCreate_returnsIdempotentOnDuplicateKey() {
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());
        when(claimApplicationIssueRepository.hasOpenIssue(CLAIM_APPLICATION_NO)).thenReturn(false);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("Duplicate entry"))
                .when(claimApplicationIssueRepository).insert(any(ClaimApplicationIssue.class));

        ClaimApplicationIssueIntakeResult result = useCase.receive(command);

        assertThat(result.created()).isFalse();
        verify(claimApplicationRepository, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("申请不存在：抛 ISSUE_APPLICATION_NOT_FOUND")
    void receive_applicationNotFound_throwsException() {
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.receive(command))
                .isInstanceOf(ClaimApplicationIssueException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND)
                .hasMessageContaining(CLAIM_APPLICATION_NO);
    }

    @Test
    @DisplayName("申请状态不匹配：抛 ISSUE_APPLICATION_STATUS_INVALID")
    void receive_applicationStatusInvalid_throwsException() {
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.PENDING_ACCEPT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> useCase.receive(command))
                .isInstanceOf(ClaimApplicationIssueException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID)
                .hasMessageContaining(CLAIM_APPLICATION_NO)
                .hasMessageContaining("PENDING_ACCEPT");
    }

    @Test
    @DisplayName("已有未关闭问题件：抛 ISSUE_ALREADY_OPEN")
    void receive_existingOpenIssue_throwsException() {
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
        ClaimApplication application = mockApplicationInStatus(ClaimApplicationStatus.WAIT_AUDIT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());
        when(claimApplicationIssueRepository.hasOpenIssue(CLAIM_APPLICATION_NO)).thenReturn(true);

        assertThatThrownBy(() -> useCase.receive(command))
                .isInstanceOf(ClaimApplicationIssueException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_ALREADY_OPEN)
                .hasMessageContaining(CLAIM_APPLICATION_NO);
    }

    private ClaimApplication mockApplicationInStatus(ClaimApplicationStatus status) {
        ClaimApplication application = mock(ClaimApplication.class);
        when(application.getStatus()).thenReturn(status);
        when(application.isWaitAudit()).thenReturn(status == ClaimApplicationStatus.WAIT_AUDIT);
        return application;
    }
}
