package com.wangguangwu.claim.application.application.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.application.exception.ApplicationException;
import com.wangguangwu.claim.application.application.exception.ClaimApplicationIntakeConflictException;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.application.port.model.ClaimApplicationIntakeLookup;
import com.wangguangwu.claim.application.application.port.model.ClaimApplicationIntakeLookupKind;
import com.wangguangwu.claim.application.application.port.model.ClaimApplicationIntakeSnapshot;
import com.wangguangwu.claim.application.application.port.ClaimApplicationIntakeLock;
import com.wangguangwu.claim.application.application.port.ClaimApplicationIntakePolicy;
import com.wangguangwu.claim.application.application.port.ClaimApplicationIntakeSnapshotQuery;
import com.wangguangwu.claim.application.application.port.ClaimApplicationNoGenerator;
import com.wangguangwu.claim.application.shared.exception.RepositoryConflictException;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 签收用例单元测试：验证编号分流、快照失效、冲突恢复与幂等行为。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("签收用例")
class ClaimApplicationIntakeUseCaseTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261031-001";
    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";
    private static final String BATCH_NO_PATTERN = "B\\d{8}[0-9a-f]{32}";
    private static final String REQUEST_ID = "req-001";
    private static final String TRACE_ID = "trace-001";

    private ClaimApplicationIntakeLock lock;
    private ClaimApplicationIntakeSnapshotQuery snapshotQuery;
    private ClaimApplicationNoGenerator noGenerator;
    private ClaimApplicationIntakePolicy policy;
    private ClaimApplicationIntakeTransactionService transactionService;
    private ClaimApplicationIntakeConflictResolver conflictResolver;

    private ClaimApplicationIntakeUseCase useCase;

    @BeforeEach
    void setUp() {
        lock = mock(ClaimApplicationIntakeLock.class);
        snapshotQuery = mock(ClaimApplicationIntakeSnapshotQuery.class);
        noGenerator = mock(ClaimApplicationNoGenerator.class);
        policy = mock(ClaimApplicationIntakePolicy.class);
        transactionService = mock(ClaimApplicationIntakeTransactionService.class);
        conflictResolver = mock(ClaimApplicationIntakeConflictResolver.class);

        useCase = new ClaimApplicationIntakeUseCase(lock, snapshotQuery, noGenerator, policy, transactionService,
                conflictResolver);

        when(policy.uniqueKeyRetryTimes()).thenReturn(5);
        when(lock.execute(eq(TENANT_CODE), eq(EXTERNAL_NO), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> action = invocation.getArgument(2);
                    return action.get();
                });
    }

    @Test
    @DisplayName("未携带平台编号：缓存未命中 → 生成编号 → 创建 → 标记存在")
    void intake_withoutPlatformNumber_absentLookup_createsApplication() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO);
        when(snapshotQuery.lookup(TENANT_CODE, EXTERNAL_NO)).thenReturn(ClaimApplicationIntakeLookup.absent());
        when(noGenerator.nextClaimApplicationNo()).thenReturn(CLAIM_APPLICATION_NO);
        when(transactionService.intake(any(), eq(ClaimApplicationIntakeLookupKind.ABSENT), eq(CLAIM_APPLICATION_NO),
                eq(REQUEST_ID), eq(TRACE_ID)))
                .thenReturn(Optional.of(ClaimApplicationIntakeOutcome.withoutSnapshot(
                        new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO,
                                ClaimApplicationIntakeDisposition.CREATED))));

        ClaimApplicationIntakeResult result = useCase.intake(command, REQUEST_ID, TRACE_ID);

        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.CREATED);
        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        verify(snapshotQuery).markMutable(TENANT_CODE, EXTERNAL_NO);
    }

    @Test
    @DisplayName("未携带平台编号：可复用快照只写轨迹，不读库")
    void intake_withoutPlatformNumber_reusableLookup_returnsReused() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO);
        ClaimApplicationIntakeSnapshot snapshot = new ClaimApplicationIntakeSnapshot(CLAIM_APPLICATION_NO,
                ClaimApplicationStatus.WAIT_AUDIT);
        when(snapshotQuery.lookup(TENANT_CODE, EXTERNAL_NO))
                .thenReturn(ClaimApplicationIntakeLookup.reusable(snapshot));
        when(transactionService.recordReused(CLAIM_APPLICATION_NO, REQUEST_ID, TRACE_ID))
                .thenReturn(new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO,
                        ClaimApplicationIntakeDisposition.REUSED));

        ClaimApplicationIntakeResult result = useCase.intake(command, REQUEST_ID, TRACE_ID);

        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.REUSED);
        verify(transactionService, never()).intake(any(), any(), any(), any(), any());
        verify(noGenerator, never()).nextClaimApplicationNo();
    }

    @Test
    @DisplayName("未携带平台编号：库中已存在时回源复用并回填快照")
    void intake_withoutPlatformNumber_existingLookup_returnsReusedAndMarksReusable() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO);
        when(snapshotQuery.lookup(TENANT_CODE, EXTERNAL_NO)).thenReturn(ClaimApplicationIntakeLookup.existing());
        ClaimApplicationIntakeSnapshot snapshot = new ClaimApplicationIntakeSnapshot(CLAIM_APPLICATION_NO,
                ClaimApplicationStatus.WAIT_AUDIT);
        when(transactionService.intake(any(), eq(ClaimApplicationIntakeLookupKind.EXISTING), eq(null),
                eq(REQUEST_ID), eq(TRACE_ID)))
                .thenReturn(Optional.of(ClaimApplicationIntakeOutcome.withSnapshot(
                        new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO,
                                ClaimApplicationIntakeDisposition.REUSED), snapshot)));

        ClaimApplicationIntakeResult result = useCase.intake(command, REQUEST_ID, TRACE_ID);

        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.REUSED);
        verify(snapshotQuery).markReusable(TENANT_CODE, EXTERNAL_NO, snapshot);
        verify(snapshotQuery, never()).markMutable(TENANT_CODE, EXTERNAL_NO);
    }

    @Test
    @DisplayName("携带平台编号：重新申请成功后失效签收快照且跳过判定缓存")
    void intake_withPlatformNumber_reapplied_invalidatesSnapshot() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO,
                CLAIM_APPLICATION_NO);
        when(transactionService.intakeExisting(any(), eq(CLAIM_APPLICATION_NO), any(), eq(REQUEST_ID),
                eq(TRACE_ID)))
                .thenReturn(ClaimApplicationIntakeOutcome.withoutSnapshot(
                        new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO,
                                ClaimApplicationIntakeDisposition.REAPPLIED)));

        ClaimApplicationIntakeResult result = useCase.intake(command, REQUEST_ID, TRACE_ID);

        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.REAPPLIED);
        ArgumentCaptor<ClaimApplication> submittedCaptor = ArgumentCaptor.forClass(ClaimApplication.class);
        verify(transactionService).intakeExisting(submittedCaptor.capture(), eq(CLAIM_APPLICATION_NO), any(),
                eq(REQUEST_ID), eq(TRACE_ID));
        assertThat(submittedCaptor.getValue().getMaterials())
                .isNotEmpty()
                .allSatisfy(material -> assertThat(material.getBatchNo()).matches(BATCH_NO_PATTERN));
        verify(snapshotQuery, never()).lookup(any(), any());
        verify(snapshotQuery).markMutable(TENANT_CODE, EXTERNAL_NO);
    }

    @Test
    @DisplayName("携带平台编号：补材成功后失效签收快照")
    void intake_withPlatformNumber_supplemented_invalidatesSnapshot() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO,
                CLAIM_APPLICATION_NO);
        when(transactionService.intakeExisting(any(), eq(CLAIM_APPLICATION_NO), any(), eq(REQUEST_ID),
                eq(TRACE_ID)))
                .thenReturn(ClaimApplicationIntakeOutcome.withoutSnapshot(
                        new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO,
                                ClaimApplicationIntakeDisposition.SUPPLEMENTED)));

        ClaimApplicationIntakeResult result = useCase.intake(command, REQUEST_ID, TRACE_ID);

        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.SUPPLEMENTED);
        verify(snapshotQuery).markMutable(TENANT_CODE, EXTERNAL_NO);
    }

    @Test
    @DisplayName("携带平台编号：编号与库中记录不一致时返回 400")
    void intake_withPlatformNumber_mismatch_throwsInvalid() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO,
                CLAIM_APPLICATION_NO);
        when(transactionService.intakeExisting(any(), eq(CLAIM_APPLICATION_NO), any(), eq(REQUEST_ID),
                eq(TRACE_ID)))
                .thenThrow(new ApplicationException(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_INVALID,
                        "编号不一致"));

        assertThatThrownBy(() -> useCase.intake(command, REQUEST_ID, TRACE_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_INVALID);
        verify(snapshotQuery, never()).markMutable(any(), any());
    }

    @Test
    @DisplayName("携带平台编号：保存冲突交由独立入口按补材标记重新分类")
    void intake_withPlatformNumber_saveConflict_resolvesInNewTransaction() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO,
                CLAIM_APPLICATION_NO);
        when(transactionService.intakeExisting(any(), eq(CLAIM_APPLICATION_NO), any(), eq(REQUEST_ID),
                eq(TRACE_ID)))
                .thenThrow(new ClaimApplicationIntakeConflictException(CLAIM_APPLICATION_NO, 1, true));
        when(conflictResolver.resolve(TENANT_CODE, EXTERNAL_NO, CLAIM_APPLICATION_NO, 1, true, REQUEST_ID, TRACE_ID))
                .thenReturn(new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO,
                        ClaimApplicationIntakeDisposition.REUSED));

        ClaimApplicationIntakeResult result = useCase.intake(command, REQUEST_ID, TRACE_ID);

        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.REUSED);
        verify(snapshotQuery, never()).markMutable(any(), any());
    }

    @Test
    @DisplayName("未携带平台编号：唯一键冲突重试耗尽返回处理中")
    void intake_withoutPlatformNumber_retryExhausted_throwsBusy() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO);
        when(policy.uniqueKeyRetryTimes()).thenReturn(1);
        when(snapshotQuery.lookup(TENANT_CODE, EXTERNAL_NO)).thenReturn(ClaimApplicationIntakeLookup.absent());
        when(noGenerator.nextClaimApplicationNo()).thenReturn(CLAIM_APPLICATION_NO);
        when(transactionService.intake(any(), eq(ClaimApplicationIntakeLookupKind.ABSENT), eq(CLAIM_APPLICATION_NO),
                eq(REQUEST_ID), eq(TRACE_ID)))
                .thenThrow(new RepositoryConflictException("duplicate"));
        when(transactionService.resolveAfterKeyConflict(any(), eq(REQUEST_ID), eq(TRACE_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.intake(command, REQUEST_ID, TRACE_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_BUSY)
                .hasMessageContaining(TENANT_CODE)
                .hasMessageContaining(EXTERNAL_NO);
    }
}
