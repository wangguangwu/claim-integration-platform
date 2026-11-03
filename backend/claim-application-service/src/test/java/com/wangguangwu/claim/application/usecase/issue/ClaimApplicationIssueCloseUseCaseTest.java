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
import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.claim.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.domain.exception.DomainException;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueCloseCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueCloseResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 问题件关闭用例单元测试：验证聚合关闭、幂等与保存冲突处理。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("问题件关闭用例")
class ClaimApplicationIssueCloseUseCaseTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261031-001";
    private static final String ISSUE_NO = "ISS202610310001";
    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";
    private static final String AUDITOR = "auditor_lisi";

    private ClaimApplicationRepository claimApplicationRepository;
    private ClaimApplicationIssueTransactionService transactionService;

    private ClaimApplicationIssueCloseUseCase useCase;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        transactionService = mock(ClaimApplicationIssueTransactionService.class);

        useCase = new ClaimApplicationIssueCloseUseCase(claimApplicationRepository, transactionService);
    }

    @Test
    @DisplayName("首次关闭：根关闭问题件并恢复等待审核")
    void close_firstTime_closesIssueAndReturnsClosed() {
        ClaimApplication application = application(ClaimApplicationStatus.ISSUE, openIssue());

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(application));
        when(transactionService.save(application)).thenReturn(true);

        ClaimApplicationIssueCloseResult result = useCase.close(command());

        assertThat(result.issueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.closed()).isTrue();
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.WAIT_AUDIT);
        assertThat(application.getIssues().get(0).isClosed()).isTrue();
    }

    @Test
    @DisplayName("幂等关闭：问题件已关闭时不保存")
    void close_alreadyClosed_returnsIdempotentSuccess() {
        ClaimApplicationIssue closedIssue = openIssue();
        closedIssue.close(AUDITOR);
        ClaimApplication application = application(ClaimApplicationStatus.WAIT_AUDIT, closedIssue);

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(application));

        ClaimApplicationIssueCloseResult result = useCase.close(command());

        assertThat(result.closed()).isFalse();
        verify(transactionService, never()).save(any());
    }

    @Test
    @DisplayName("问题件不存在：抛 ISSUE_NOT_FOUND")
    void close_issueNotFound_throwsException() {
        when(claimApplicationRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.close(command()))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND)
                .hasMessageContaining(ISSUE_NO);
    }

    @Test
    @DisplayName("保存冲突：保存事务之外重读后按已关闭幂等返回")
    void close_saveConflict_rereadsClosedIssue() {
        ClaimApplication staleApplication = application(ClaimApplicationStatus.ISSUE, openIssue());
        ClaimApplicationIssue closedIssue = openIssue();
        closedIssue.close(AUDITOR);
        ClaimApplication currentApplication = application(ClaimApplicationStatus.WAIT_AUDIT, closedIssue);

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO))
                .thenReturn(Optional.of(staleApplication), Optional.of(currentApplication));
        when(transactionService.save(staleApplication)).thenReturn(false);

        ClaimApplicationIssueCloseResult result = useCase.close(command());

        assertThat(result.closed()).isFalse();
    }

    @Test
    @DisplayName("保存冲突：重读后问题件仍未关闭时唯一重试成功")
    void close_saveConflict_retriesOnceAndSucceeds() {
        ClaimApplication staleApplication = application(ClaimApplicationStatus.ISSUE, openIssue());
        ClaimApplication currentApplication = application(ClaimApplicationStatus.ISSUE, openIssue());

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO))
                .thenReturn(Optional.of(staleApplication), Optional.of(currentApplication));
        when(transactionService.save(staleApplication)).thenReturn(false);
        when(transactionService.save(currentApplication)).thenReturn(true);

        ClaimApplicationIssueCloseResult result = useCase.close(command());

        assertThat(result.closed()).isTrue();
    }

    @Test
    @DisplayName("保存冲突：重试仍版本过期时返回状态冲突")
    void close_saveConflictTwice_throwsIssueStatusInvalid() {
        ClaimApplication staleApplication = application(ClaimApplicationStatus.ISSUE, openIssue());
        ClaimApplication currentApplication = application(ClaimApplicationStatus.ISSUE, openIssue());

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO))
                .thenReturn(Optional.of(staleApplication), Optional.of(currentApplication));
        when(transactionService.save(staleApplication)).thenReturn(false);
        when(transactionService.save(currentApplication)).thenReturn(false);

        assertThatThrownBy(() -> useCase.close(command()))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID);
    }

    private ClaimApplicationIssueCloseCommand command() {
        return new ClaimApplicationIssueCloseCommand(ISSUE_NO, AUDITOR);
    }

    private ClaimApplication application(ClaimApplicationStatus status, ClaimApplicationIssue issue) {
        return ClaimApplication.restore(1L, TENANT_CODE, CLAIM_APPLICATION_NO, EXTERNAL_NO, status, 1, null,
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                new AccidentPerson("王五", Gender.MALE, "身份证", "110101199003033456"),
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                0L, List.of(), List.of(issue));
    }

    private ClaimApplicationIssue openIssue() {
        return ClaimApplicationIssue.create(ISSUE_NO, "缺少发票", "auditor-1", "{}");
    }
}
