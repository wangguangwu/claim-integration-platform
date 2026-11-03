package com.wangguangwu.claim.application.usecase.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.usecase.exception.ApplicationException;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionAction;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionType;
import com.wangguangwu.claim.application.usecase.integration.interaction.port.InteractionLogRepository;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeDisposition;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 签收保存冲突解析单元测试：验证在新事务中重读后的三类分类结果。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@DisplayName("签收保存冲突解析")
class ClaimApplicationIntakeConflictResolverTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261103-001";
    private static final String CLAIM_APPLICATION_NO = "A2026110300000001";
    private static final String REQUEST_ID = "req-001";
    private static final String TRACE_ID = "trace-001";

    private ClaimApplicationRepository claimApplicationRepository;
    private InteractionLogRepository interactionLogRepository;
    private ClaimApplicationIntakeConflictResolver resolver;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        interactionLogRepository = mock(InteractionLogRepository.class);
        ClaimApplicationLifecycleRecordFactory recordFactory = new ClaimApplicationLifecycleRecordFactory(
                new ObjectMapper());
        resolver = new ClaimApplicationIntakeConflictResolver(claimApplicationRepository, interactionLogRepository,
                recordFactory);
    }

    @Test
    @DisplayName("受理尝试次数已增大：按并发同类请求复用并写复用轨迹")
    void resolve_attemptIncreased_returnsReused() {
        when(claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(TENANT_CODE, EXTERNAL_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 2)));

        ClaimApplicationIntakeResult result = resolver.resolve(TENANT_CODE, EXTERNAL_NO, CLAIM_APPLICATION_NO, 1,
                false, REQUEST_ID, TRACE_ID);

        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.REUSED);
        verify(interactionLogRepository, times(2)).insert(any());
    }

    @Test
    @DisplayName("补材冲突且状态已离开问题件：返回问题件状态非法")
    void resolve_supplementStatusChanged_throwsIssueStatusInvalid() {
        when(claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(TENANT_CODE, EXTERNAL_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.WAIT_AUDIT, 1)));

        assertThatThrownBy(() -> resolver.resolve(TENANT_CODE, EXTERNAL_NO, CLAIM_APPLICATION_NO, 1, true, REQUEST_ID,
                TRACE_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID)
                .hasMessageContaining("当前状态=WAIT_AUDIT");
        verify(interactionLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("补材冲突且申请仍处于问题件：按处理中要求租户重试")
    void resolve_supplementStillIssue_throwsBusy() {
        when(claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(TENANT_CODE, EXTERNAL_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.ISSUE, 1)));

        assertThatThrownBy(() -> resolver.resolve(TENANT_CODE, EXTERNAL_NO, CLAIM_APPLICATION_NO, 1, true, REQUEST_ID,
                TRACE_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_BUSY);
    }

    @Test
    @DisplayName("重新申请冲突且尝试次数未变：按处理中要求租户重试")
    void resolve_reapplyAttemptUnchanged_throwsBusy() {
        when(claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(TENANT_CODE, EXTERNAL_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.ACCEPT_FAILED, 1)));

        assertThatThrownBy(() -> resolver.resolve(TENANT_CODE, EXTERNAL_NO, CLAIM_APPLICATION_NO, 1, false, REQUEST_ID,
                TRACE_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_BUSY);
    }

    @Test
    @DisplayName("重读不到申请：按处理中返回且不写轨迹")
    void resolve_applicationMissing_throwsBusy() {
        when(claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(TENANT_CODE, EXTERNAL_NO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(TENANT_CODE, EXTERNAL_NO, CLAIM_APPLICATION_NO, 1, false, REQUEST_ID,
                TRACE_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_BUSY);
        verify(interactionLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("复用轨迹使用签收类型并按开始与复用两条写入")
    void resolve_attemptIncreased_recordsIntakeStartedAndReused() {
        when(claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(TENANT_CODE, EXTERNAL_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.ACCEPTED, 2)));

        resolver.resolve(TENANT_CODE, EXTERNAL_NO, CLAIM_APPLICATION_NO, 1, false, REQUEST_ID, TRACE_ID);

        verify(interactionLogRepository).insert(org.mockito.ArgumentMatchers.argThat(
                log -> InteractionType.CLAIM_APPLICATION_INTAKE.name().equals(log.interactionType())
                        && InteractionAction.STARTED.name().equals(log.action())));
        verify(interactionLogRepository).insert(org.mockito.ArgumentMatchers.argThat(
                log -> InteractionType.CLAIM_APPLICATION_INTAKE.name().equals(log.interactionType())
                        && InteractionAction.REUSED.name().equals(log.action())));
    }

    private ClaimApplication application(ClaimApplicationStatus status, int attempt) {
        return ClaimApplication.restore(1L, TENANT_CODE, CLAIM_APPLICATION_NO, EXTERNAL_NO, status, attempt,
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                null,
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                0L, List.of(), List.of());
    }
}
