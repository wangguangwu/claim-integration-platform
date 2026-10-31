package com.wangguangwu.claim.application.usecase.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeLookup;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeLookupKind;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakePolicy;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeSnapshot;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationIntakeLock;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationIntakeSnapshotQuery;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationNoGenerator;
import com.wangguangwu.claim.application.usecase.intake.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeDisposition;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 签收用例单元测试：验证重试与幂等行为。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("签收用例")
class ClaimApplicationIntakeUseCaseTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261031-001";
    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";
    private static final String REQUEST_ID = "req-001";
    private static final String TRACE_ID = "trace-001";

    private ClaimApplicationIntakeLock lock;
    private ClaimApplicationIntakeSnapshotQuery snapshotQuery;
    private ClaimApplicationNoGenerator noGenerator;
    private ClaimApplicationIntakePolicy policy;
    private ClaimApplicationIntakeTransactionService transactionService;

    private ClaimApplicationIntakeUseCase useCase;

    @BeforeEach
    void setUp() {
        lock = mock(ClaimApplicationIntakeLock.class);
        snapshotQuery = mock(ClaimApplicationIntakeSnapshotQuery.class);
        noGenerator = mock(ClaimApplicationNoGenerator.class);
        policy = mock(ClaimApplicationIntakePolicy.class);
        transactionService = mock(ClaimApplicationIntakeTransactionService.class);

        useCase = new ClaimApplicationIntakeUseCase(lock, snapshotQuery, noGenerator, policy, transactionService);

        when(policy.uniqueKeyRetryTimes()).thenReturn(5);
        when(lock.execute(eq(TENANT_CODE), eq(EXTERNAL_NO), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> action = invocation.getArgument(2);
                    return action.get();
                });
    }

    @Test
    @DisplayName("首次创建：缓存未命中 → 生成编号 → 事务内创建 → 标记存在 → 返回 CREATED")
    void intake_absentLookup_createsApplication() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO);
        when(snapshotQuery.lookup(TENANT_CODE, EXTERNAL_NO)).thenReturn(ClaimApplicationIntakeLookup.absent());
        when(noGenerator.nextClaimApplicationNo()).thenReturn(CLAIM_APPLICATION_NO);
        when(transactionService.intake(any(), eq(ClaimApplicationIntakeLookupKind.ABSENT), eq(CLAIM_APPLICATION_NO),
                eq(REQUEST_ID), eq(TRACE_ID)))
                .thenReturn(ClaimApplicationIntakeOutcome.withoutSnapshot(
                        new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO, ClaimApplicationIntakeDisposition.CREATED)));

        ClaimApplicationIntakeResult result = useCase.intake(command, REQUEST_ID, TRACE_ID);

        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.CREATED);
        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        verify(snapshotQuery).markMutable(TENANT_CODE, EXTERNAL_NO);
    }

    @Test
    @DisplayName("重复命中缓存：直接返回 REUSED，不读聚合、不调用外部系统")
    void intake_reusableLookup_returnsReusedWithoutDatabaseAccess() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO);
        ClaimApplicationIntakeSnapshot snapshot = new ClaimApplicationIntakeSnapshot(CLAIM_APPLICATION_NO,
                ClaimApplicationStatus.WAIT_AUDIT);
        when(snapshotQuery.lookup(TENANT_CODE, EXTERNAL_NO)).thenReturn(ClaimApplicationIntakeLookup.reusable(snapshot));
        when(transactionService.recordReused(CLAIM_APPLICATION_NO, REQUEST_ID, TRACE_ID))
                .thenReturn(new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO, ClaimApplicationIntakeDisposition.REUSED));

        ClaimApplicationIntakeResult result = useCase.intake(command, REQUEST_ID, TRACE_ID);

        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.REUSED);
        assertThat(result.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        verify(transactionService, never()).intake(any(), any(), any(), any(), any());
        verify(noGenerator, never()).nextClaimApplicationNo();
    }

    @Test
    @DisplayName("重复未命中缓存：回源后返回 REUSED 并回填快照")
    void intake_existingLookup_returnsReusedAndMarksReusable() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO);
        when(snapshotQuery.lookup(TENANT_CODE, EXTERNAL_NO)).thenReturn(ClaimApplicationIntakeLookup.existing());
        ClaimApplicationIntakeSnapshot snapshot = new ClaimApplicationIntakeSnapshot(CLAIM_APPLICATION_NO,
                ClaimApplicationStatus.WAIT_AUDIT);
        when(transactionService.intake(any(), eq(ClaimApplicationIntakeLookupKind.EXISTING), eq(null),
                eq(REQUEST_ID), eq(TRACE_ID)))
                .thenReturn(ClaimApplicationIntakeOutcome.withSnapshot(
                        new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO, ClaimApplicationIntakeDisposition.REUSED),
                        snapshot));

        ClaimApplicationIntakeResult result = useCase.intake(command, REQUEST_ID, TRACE_ID);

        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.REUSED);
        verify(snapshotQuery).markReusable(TENANT_CODE, EXTERNAL_NO, snapshot);
    }

    @Test
    @DisplayName("唯一键冲突重试成功：resolveAfterDuplicateKey 返回结果 → 返回 REUSED")
    void intake_duplicateKeyResolved_returnsReused() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO);
        when(snapshotQuery.lookup(TENANT_CODE, EXTERNAL_NO)).thenReturn(ClaimApplicationIntakeLookup.absent());
        when(noGenerator.nextClaimApplicationNo()).thenReturn(CLAIM_APPLICATION_NO);
        when(transactionService.intake(any(), eq(ClaimApplicationIntakeLookupKind.ABSENT), eq(CLAIM_APPLICATION_NO),
                eq(REQUEST_ID), eq(TRACE_ID)))
                .thenThrow(new DuplicateKeyException("duplicate"))
                .thenReturn(ClaimApplicationIntakeOutcome.withoutSnapshot(
                        new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO, ClaimApplicationIntakeDisposition.REUSED)));
        when(transactionService.resolveAfterDuplicateKey(any(), eq(REQUEST_ID), eq(TRACE_ID)))
                .thenReturn(Optional.of(ClaimApplicationIntakeOutcome.withoutSnapshot(
                        new ClaimApplicationIntakeResult(CLAIM_APPLICATION_NO, ClaimApplicationIntakeDisposition.REUSED))));

        ClaimApplicationIntakeResult result = useCase.intake(command, REQUEST_ID, TRACE_ID);

        assertThat(result.disposition()).isEqualTo(ClaimApplicationIntakeDisposition.REUSED);
        verify(snapshotQuery).markMutable(TENANT_CODE, EXTERNAL_NO);
    }

    @Test
    @DisplayName("重试耗尽：连续 5 次唯一键冲突且解析失败 → 抛 IllegalStateException")
    void intake_retryExhausted_throwsIllegalStateException() {
        ClaimApplicationIntakeCommand command = IntakeCommandFixture.command(TENANT_CODE, EXTERNAL_NO);
        when(snapshotQuery.lookup(TENANT_CODE, EXTERNAL_NO)).thenReturn(ClaimApplicationIntakeLookup.absent());
        when(noGenerator.nextClaimApplicationNo()).thenReturn(CLAIM_APPLICATION_NO);
        when(transactionService.intake(any(), eq(ClaimApplicationIntakeLookupKind.ABSENT), eq(CLAIM_APPLICATION_NO),
                eq(REQUEST_ID), eq(TRACE_ID)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(transactionService.resolveAfterDuplicateKey(any(), eq(REQUEST_ID), eq(TRACE_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.intake(command, REQUEST_ID, TRACE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TENANT_CODE)
                .hasMessageContaining(EXTERNAL_NO);
    }
}
