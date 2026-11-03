package com.wangguangwu.claim.application.application.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.person.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.shared.exception.DomainException;
import com.wangguangwu.claim.application.application.port.ClaimApplicationIntakeSnapshotQuery;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIssueIntakeCommand;
import com.wangguangwu.claim.application.shared.exception.RepositoryConflictException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 问题件下发用例单元测试：验证聚合保存、幂等与快照失效。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("问题件下发用例")
class ClaimApplicationIssueIntakeUseCaseTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261031-001";
    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";
    private static final String ISSUE_NO = "ISS202610310001";
    private static final String REASON = "缺少病历首页";
    private static final String AUDITOR = "auditor_zhangsan";
    private static final String ISSUE_CONTENT = "{\"missingMaterials\":[\"病历首页\"]}";

    private ClaimApplicationRepository claimApplicationRepository;
    private ClaimApplicationIssueTransactionService transactionService;
    private ClaimApplicationIntakeSnapshotQuery intakeSnapshotQuery;

    private ClaimApplicationIssueIntakeUseCase useCase;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        transactionService = mock(ClaimApplicationIssueTransactionService.class);
        intakeSnapshotQuery = mock(ClaimApplicationIntakeSnapshotQuery.class);

        useCase = new ClaimApplicationIssueIntakeUseCase(claimApplicationRepository, transactionService,
                intakeSnapshotQuery);
    }

    @Test
    @DisplayName("首次下发：根打开问题件、保存并失效签收快照")
    void receive_firstTime_raisesIssueAndInvalidatesSnapshot() {
        ClaimApplicationIssueIntakeCommand command = command();
        ClaimApplication application = application(ClaimApplicationStatus.WAIT_AUDIT);

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(transactionService.save(application)).thenReturn(true);

        ClaimApplicationIssueIntakeResult result = useCase.receive(command);

        assertThat(result.issueNo()).isEqualTo(ISSUE_NO);
        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(result.created()).isTrue();
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.ISSUE);
        assertThat(application.getIssues()).hasSize(1);
        verify(intakeSnapshotQuery).markMutable(TENANT_CODE, EXTERNAL_NO);
    }

    @Test
    @DisplayName("幂等下发：issueNo 已存在时不加载申请、不保存")
    void receive_duplicateIssueNo_returnsIdempotentSuccess() {
        ClaimApplication application = application(ClaimApplicationStatus.ISSUE);

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.of(application));

        ClaimApplicationIssueIntakeResult result = useCase.receive(command());

        assertThat(result.created()).isFalse();
        verify(transactionService, never()).save(any());
        verify(intakeSnapshotQuery, never()).markMutable(anyString(), anyString());
    }

    @Test
    @DisplayName("并发下发：触发唯一约束时按幂等处理")
    void receive_concurrentCreate_returnsIdempotentOnDuplicateKey() {
        ClaimApplication application = application(ClaimApplicationStatus.WAIT_AUDIT);

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(transactionService.save(application)).thenThrow(new RepositoryConflictException("Duplicate entry"));

        ClaimApplicationIssueIntakeResult result = useCase.receive(command());

        assertThat(result.created()).isFalse();
    }

    @Test
    @DisplayName("保存版本过期：重读发现同号问题件已落库时按幂等返回")
    void receive_versionConflictAndIssuePersisted_returnsIdempotent() {
        ClaimApplication staleApplication = application(ClaimApplicationStatus.WAIT_AUDIT);
        ClaimApplication currentApplication = application(ClaimApplicationStatus.ISSUE);

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty(),
                Optional.of(currentApplication));
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(staleApplication));
        when(transactionService.save(staleApplication)).thenReturn(false);

        ClaimApplicationIssueIntakeResult result = useCase.receive(command());

        assertThat(result.created()).isFalse();
        verify(intakeSnapshotQuery, never()).markMutable(anyString(), anyString());
    }

    @Test
    @DisplayName("保存版本过期：重读发现申请状态已变化时返回状态冲突")
    void receive_versionConflictAndStatusChanged_throwsIssueStatusInvalid() {
        ClaimApplication staleApplication = application(ClaimApplicationStatus.WAIT_AUDIT);
        ClaimApplication changedApplication = application(ClaimApplicationStatus.PENDING_ACCEPT);

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty(), Optional.empty());
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(staleApplication), Optional.of(changedApplication));
        when(transactionService.save(staleApplication)).thenReturn(false);

        assertThatThrownBy(() -> useCase.receive(command()))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID);
    }

    @Test
    @DisplayName("保存版本过期：事务外重读后唯一重试成功")
    void receive_versionConflict_retriesOnceAndSucceeds() {
        ClaimApplication staleApplication = application(ClaimApplicationStatus.WAIT_AUDIT);
        ClaimApplication currentApplication = application(ClaimApplicationStatus.WAIT_AUDIT);

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty(), Optional.empty());
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(staleApplication), Optional.of(currentApplication));
        when(transactionService.save(staleApplication)).thenReturn(false);
        when(transactionService.save(currentApplication)).thenReturn(true);

        ClaimApplicationIssueIntakeResult result = useCase.receive(command());

        assertThat(result.created()).isTrue();
        verify(intakeSnapshotQuery).markMutable(TENANT_CODE, EXTERNAL_NO);
    }

    @Test
    @DisplayName("签收快照失效失败：只记日志，不回滚已下发的问题件")
    void receive_snapshotInvalidationFails_keepsCreatedResult() {
        ClaimApplication application = application(ClaimApplicationStatus.WAIT_AUDIT);

        when(claimApplicationRepository.findByIssueNo(ISSUE_NO)).thenReturn(Optional.empty());
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application));
        when(transactionService.save(application)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis 不可用"))
                .when(intakeSnapshotQuery).markMutable(TENANT_CODE, EXTERNAL_NO);

        ClaimApplicationIssueIntakeResult result = useCase.receive(command());

        assertThat(result.created()).isTrue();
    }

    private ClaimApplicationIssueIntakeCommand command() {
        return new ClaimApplicationIssueIntakeCommand(CLAIM_APPLICATION_NO, ISSUE_NO, REASON, AUDITOR,
                ISSUE_CONTENT);
    }

    private ClaimApplication application(ClaimApplicationStatus status) {
        return ClaimApplication.restore(1L, TENANT_CODE, CLAIM_APPLICATION_NO, EXTERNAL_NO, status, 1, null,
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                new AccidentPerson("王五", Gender.MALE, "身份证", "110101199003033456"),
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                0L, List.of(), List.of());
    }
}
