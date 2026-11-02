package com.wangguangwu.claim.application.usecase.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.port.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.service.IssueDomainService;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueIntakeCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueIntakeResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 问题件下发用例单元测试：验证幂等处理与领域服务委托。
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

    private IssueDomainService issueDomainService;
    private ClaimApplicationIssueRepository claimApplicationIssueRepository;

    private ClaimApplicationIssueIntakeUseCase useCase;

    @BeforeEach
    void setUp() {
        issueDomainService = mock(IssueDomainService.class);
        claimApplicationIssueRepository = mock(ClaimApplicationIssueRepository.class);

        useCase = new ClaimApplicationIssueIntakeUseCase(issueDomainService, claimApplicationIssueRepository);
    }

    @Test
    @DisplayName("首次下发：调用领域服务，返回 created=true")
    void receive_firstTime_callsDomainServiceAndReturnsCreated() {
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
        ClaimApplicationIssue issue = mock(ClaimApplicationIssue.class);
        
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());
        when(issueDomainService.raiseIssue(
                eq(CLAIM_APPLICATION_NO), eq(ISSUE_NO), eq(REASON), eq(AUDITOR), eq(ISSUE_CONTENT)))
                .thenReturn(issue);

        ClaimApplicationIssueIntakeResult result = useCase.receive(command);

        assertThat(result.issueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(result.created()).isTrue();
        verify(issueDomainService).raiseIssue(CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
    }

    @Test
    @DisplayName("幂等下发：issueNo 已存在 → 返回成功，不调用领域服务")
    void receive_duplicateIssueNo_returnsIdempotentSuccessWithoutCallingDomainService() {
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
        ClaimApplicationIssue existingIssue = mock(ClaimApplicationIssue.class);
        
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(existingIssue));

        ClaimApplicationIssueIntakeResult result = useCase.receive(command);

        assertThat(result.issueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(result.created()).isFalse();
        verify(issueDomainService, never()).raiseIssue(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("并发下发：触发唯一约束 → 按幂等处理，返回 created=false")
    void receive_concurrentCreate_returnsIdempotentOnDuplicateKey() {
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR, ISSUE_CONTENT);
        
        when(claimApplicationIssueRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());
        when(issueDomainService.raiseIssue(
                eq(CLAIM_APPLICATION_NO), eq(ISSUE_NO), eq(REASON), eq(AUDITOR), eq(ISSUE_CONTENT)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        ClaimApplicationIssueIntakeResult result = useCase.receive(command);

        assertThat(result.created()).isFalse();
    }
}
