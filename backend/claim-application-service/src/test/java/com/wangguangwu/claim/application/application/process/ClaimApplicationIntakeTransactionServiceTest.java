package com.wangguangwu.claim.application.application.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.domain.claim.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.Applicant;
import com.wangguangwu.claim.application.domain.claim.Gender;
import com.wangguangwu.claim.application.domain.claim.Payee;
import com.wangguangwu.claim.application.domain.claim.PrimaryInsured;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.application.port.InteractionLogRepository;
import com.wangguangwu.claim.application.application.port.OutboxEventRepository;
import com.wangguangwu.claim.application.application.port.model.ClaimApplicationIntakeLookupKind;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 签收事务服务单元测试：验证已完成申请按重复请求复用，不触发重新申请与补材。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@DisplayName("签收事务服务")
class ClaimApplicationIntakeTransactionServiceTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261103-001";
    private static final String CLAIM_APPLICATION_NO = "A2026110300000001";
    private static final String BATCH_NO = "B2026110300000001";
    private static final String REQUEST_ID = "req-025-001";
    private static final String TRACE_ID = "trace-025-001";

    private ClaimApplicationRepository claimApplicationRepository;
    private InteractionLogRepository interactionLogRepository;
    private OutboxEventRepository outboxEventRepository;

    private ClaimApplicationIntakeTransactionService transactionService;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        interactionLogRepository = mock(InteractionLogRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);

        transactionService = new ClaimApplicationIntakeTransactionService(claimApplicationRepository,
                interactionLogRepository, outboxEventRepository,
                new ClaimApplicationLifecycleRecordFactory(new ObjectMapper()));
    }

    @Test
    @DisplayName("未携带平台编号：已完成申请返回复用并带出终态快照，不写库")
    void intake_completedApplication_returnsReusedWithoutSaving() {
        when(claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(TENANT_CODE, EXTERNAL_NO))
                .thenReturn(Optional.of(completedApplication()));

        Optional<ClaimApplicationIntakeOutcome> outcome = transactionService.intake(submitted(),
                ClaimApplicationIntakeLookupKind.EXISTING, null, REQUEST_ID, TRACE_ID);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().result().disposition()).isEqualTo(ClaimApplicationIntakeDisposition.REUSED);
        assertThat(outcome.get().reusableSnapshot()).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
            assertThat(snapshot.status()).isEqualTo(ClaimApplicationStatus.COMPLETED);
        });
        verify(claimApplicationRepository, never()).save(any());
        verify(outboxEventRepository, never()).insert(any());
        verify(interactionLogRepository, times(2)).insert(any());
    }

    @Test
    @DisplayName("携带平台编号：已完成申请不触发重新申请与补材，直接返回复用")
    void intakeExisting_completedApplication_returnsReusedWithoutModifying() {
        ClaimApplication completedApplication = completedApplication();
        when(claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(TENANT_CODE, EXTERNAL_NO))
                .thenReturn(Optional.of(completedApplication));

        ClaimApplicationIntakeOutcome outcome = transactionService.intakeExisting(submitted(), CLAIM_APPLICATION_NO,
                BATCH_NO, REQUEST_ID, TRACE_ID);

        assertThat(outcome.result().disposition()).isEqualTo(ClaimApplicationIntakeDisposition.REUSED);
        assertThat(outcome.result().claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(outcome.reusableSnapshot()).hasValueSatisfying(snapshot ->
                assertThat(snapshot.status()).isEqualTo(ClaimApplicationStatus.COMPLETED));
        assertThat(completedApplication.getStatus()).isEqualTo(ClaimApplicationStatus.COMPLETED);
        assertThat(completedApplication.getClaimApplicationAttempt()).isEqualTo(1);
        verify(claimApplicationRepository, never()).save(any());
        verify(outboxEventRepository, never()).insert(any());
        verify(interactionLogRepository, times(2)).insert(any());
    }

    private ClaimApplication completedApplication() {
        return ClaimApplication.restore(1L, TENANT_CODE, CLAIM_APPLICATION_NO, EXTERNAL_NO,
                ClaimApplicationStatus.COMPLETED, 1, LocalDateTime.of(2026, 11, 3, 12, 0),
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                new AccidentPerson("王五", Gender.MALE, "身份证", "110101199003033456"),
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                0L, List.of(), List.of());
    }

    private ClaimApplication submitted() {
        return ClaimApplication.submit(TENANT_CODE, EXTERNAL_NO,
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                new AccidentPerson("王五", Gender.MALE, "身份证", "110101199003033456"),
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                List.of(new MaterialSubmission("INVOICE", "https://example.com/invoice.pdf")), BATCH_NO);
    }
}
