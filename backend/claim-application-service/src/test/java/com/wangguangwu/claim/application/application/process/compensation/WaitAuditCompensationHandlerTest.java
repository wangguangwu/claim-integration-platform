package com.wangguangwu.claim.application.application.process.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.application.command.AuditSignedCommand;
import com.wangguangwu.claim.application.application.port.AuditSystemPort;
import com.wangguangwu.claim.application.application.port.ClaimApplicationCompensationPolicy;
import com.wangguangwu.claim.application.application.port.model.AuditDeliveryRequest;
import com.wangguangwu.claim.application.application.port.model.AuditSignedStatus;
import com.wangguangwu.claim.application.application.port.model.InteractionAction;
import com.wangguangwu.claim.application.application.port.model.InteractionType;
import com.wangguangwu.claim.application.application.process.AuditSignedUseCase;
import com.wangguangwu.claim.application.application.process.ClaimApplicationCompensationTransactionService;
import com.wangguangwu.claim.application.application.process.ClaimApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 等待审核滞留补偿单元测试：覆盖已签收、处理中、未找到、查询失败、空案件号与单条失败隔离。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@DisplayName("等待审核滞留补偿处理器")
class WaitAuditCompensationHandlerTest {

    private static final String CLAIM_APPLICATION_NO = "A2026110300000001";
    private static final String TENANT_CODE = "tenant-a";
    private static final String INTERACTION_NO = "ITN-0001";
    private static final String REQUEST_ID = "compensation-" + CLAIM_APPLICATION_NO;
    private static final Duration WAIT_AUDIT_TIMEOUT = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 10;

    private ClaimApplicationRepository claimApplicationRepository;
    private AuditSystemPort auditSystemPort;
    private AuditSignedUseCase auditSignedUseCase;
    private ClaimApplicationLifecycleRecordFactory recordFactory;
    private ClaimApplicationCompensationTransactionService compensationTransactionService;

    private WaitAuditCompensationHandler handler;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        auditSystemPort = mock(AuditSystemPort.class);
        auditSignedUseCase = mock(AuditSignedUseCase.class);
        recordFactory = mock(ClaimApplicationLifecycleRecordFactory.class);
        compensationTransactionService = mock(ClaimApplicationCompensationTransactionService.class);

        ClaimApplicationCompensationPolicy compensationPolicy = mock(ClaimApplicationCompensationPolicy.class);
        when(compensationPolicy.waitAuditTimeout()).thenReturn(WAIT_AUDIT_TIMEOUT);
        when(compensationPolicy.batchSize()).thenReturn(BATCH_SIZE);
        when(recordFactory.newInteractionNo()).thenReturn(INTERACTION_NO);

        handler = new WaitAuditCompensationHandler(claimApplicationRepository, auditSystemPort, auditSignedUseCase,
                recordFactory, compensationTransactionService, compensationPolicy);
    }

    @Test
    @DisplayName("审核系统已签收：复用审核签收用例推进完成，不记录失败轨迹")
    void recover_signed_completesThroughAuditSignedUseCase() {
        ClaimApplication claimApplication = application(CLAIM_APPLICATION_NO, TENANT_CODE);
        when(claimApplicationRepository.findByStatusUpdatedBefore(eq(ClaimApplicationStatus.WAIT_AUDIT), any(),
                eq(BATCH_SIZE))).thenReturn(List.of(claimApplication));
        when(auditSystemPort.querySignedStatus(CLAIM_APPLICATION_NO))
                .thenReturn(new AuditSignedStatus.Signed(List.of("CASE-001", "CASE-002")));

        int compensatedCount = handler.recover();

        assertThat(compensatedCount).isEqualTo(1);
        ArgumentCaptor<AuditSignedCommand> commandCaptor = ArgumentCaptor.forClass(AuditSignedCommand.class);
        verify(auditSignedUseCase).record(commandCaptor.capture());
        AuditSignedCommand command = commandCaptor.getValue();
        assertThat(command.claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(command.tenantCode()).isEqualTo(TENANT_CODE);
        assertThat(command.caseNos()).containsExactly("CASE-001", "CASE-002");
        assertThat(command.eventId()).startsWith("compensation-audit-signed-");
        verifyNoInteractions(compensationTransactionService);
        verify(auditSystemPort, never()).deliver(any());
    }

    @Test
    @DisplayName("审核系统仍在处理：保持等待审核，不推进也不记录轨迹")
    void recover_processing_keepsWaiting() {
        ClaimApplication claimApplication = application(CLAIM_APPLICATION_NO, TENANT_CODE);
        when(claimApplicationRepository.findByStatusUpdatedBefore(eq(ClaimApplicationStatus.WAIT_AUDIT), any(),
                eq(BATCH_SIZE))).thenReturn(List.of(claimApplication));
        when(auditSystemPort.querySignedStatus(CLAIM_APPLICATION_NO)).thenReturn(new AuditSignedStatus.Processing());

        handler.recover();

        verifyNoInteractions(auditSignedUseCase, compensationTransactionService);
        verify(auditSystemPort, never()).deliver(any());
    }

    @Test
    @DisplayName("审核系统未找到：重新交付并记录成功交付轨迹")
    void recover_notFound_redeliversAuditDelivery() {
        ClaimApplication claimApplication = application(CLAIM_APPLICATION_NO, TENANT_CODE);
        when(claimApplicationRepository.findByStatusUpdatedBefore(eq(ClaimApplicationStatus.WAIT_AUDIT), any(),
                eq(BATCH_SIZE))).thenReturn(List.of(claimApplication));
        when(auditSystemPort.querySignedStatus(CLAIM_APPLICATION_NO)).thenReturn(new AuditSignedStatus.NotFound());

        handler.recover();

        ArgumentCaptor<AuditDeliveryRequest> requestCaptor = ArgumentCaptor.forClass(AuditDeliveryRequest.class);
        verify(auditSystemPort).deliver(requestCaptor.capture());
        assertThat(requestCaptor.getValue().claimApplicationNo()).isEqualTo(CLAIM_APPLICATION_NO);
        assertThat(requestCaptor.getValue().tenantCode()).isEqualTo(TENANT_CODE);
        verify(compensationTransactionService).recordAttempt(eq(CLAIM_APPLICATION_NO),
                eq(InteractionType.AUDIT_DELIVERY), eq(INTERACTION_NO), eq(REQUEST_ID),
                eq(InteractionAction.COMPLETED), anyString(), isNull(), isNull());
        verifyNoInteractions(auditSignedUseCase);
    }

    @Test
    @DisplayName("重新交付失败：记录失败交付轨迹")
    void recover_notFoundDeliveryFailed_recordsFailedTrace() {
        ClaimApplication claimApplication = application(CLAIM_APPLICATION_NO, TENANT_CODE);
        when(claimApplicationRepository.findByStatusUpdatedBefore(eq(ClaimApplicationStatus.WAIT_AUDIT), any(),
                eq(BATCH_SIZE))).thenReturn(List.of(claimApplication));
        when(auditSystemPort.querySignedStatus(CLAIM_APPLICATION_NO)).thenReturn(new AuditSignedStatus.NotFound());
        org.mockito.Mockito.doThrow(new IllegalStateException("审核系统不可用"))
                .when(auditSystemPort).deliver(any());

        handler.recover();

        verify(compensationTransactionService).recordAttempt(eq(CLAIM_APPLICATION_NO),
                eq(InteractionType.AUDIT_DELIVERY), eq(INTERACTION_NO), eq(REQUEST_ID),
                eq(InteractionAction.FAILED), anyString(), eq("AUDIT_DELIVERY_REQUEST_FAILED"),
                eq("审核系统不可用"));
    }

    @Test
    @DisplayName("签收状态查询失败：记录查询失败轨迹")
    void recover_queryFailed_recordsFailedTrace() {
        ClaimApplication claimApplication = application(CLAIM_APPLICATION_NO, TENANT_CODE);
        when(claimApplicationRepository.findByStatusUpdatedBefore(eq(ClaimApplicationStatus.WAIT_AUDIT), any(),
                eq(BATCH_SIZE))).thenReturn(List.of(claimApplication));
        when(auditSystemPort.querySignedStatus(CLAIM_APPLICATION_NO))
                .thenThrow(new IllegalStateException("审核系统超时"));

        handler.recover();

        verify(compensationTransactionService).recordAttempt(eq(CLAIM_APPLICATION_NO),
                eq(InteractionType.AUDIT_SIGNED), eq(INTERACTION_NO), eq(REQUEST_ID),
                eq(InteractionAction.FAILED), anyString(), eq("AUDIT_SIGNED_QUERY_FAILED"), eq("审核系统超时"));
        verifyNoInteractions(auditSignedUseCase);
    }

    @Test
    @DisplayName("已签收但案件号为空：记录案件号缺失轨迹，不推进完成")
    void recover_signedWithoutCaseNos_recordsMissingTrace() {
        ClaimApplication claimApplication = application(CLAIM_APPLICATION_NO, TENANT_CODE);
        when(claimApplicationRepository.findByStatusUpdatedBefore(eq(ClaimApplicationStatus.WAIT_AUDIT), any(),
                eq(BATCH_SIZE))).thenReturn(List.of(claimApplication));
        when(auditSystemPort.querySignedStatus(CLAIM_APPLICATION_NO))
                .thenReturn(new AuditSignedStatus.Signed(List.of()));

        handler.recover();

        verify(compensationTransactionService).recordAttempt(eq(CLAIM_APPLICATION_NO),
                eq(InteractionType.AUDIT_SIGNED), eq(INTERACTION_NO), eq(REQUEST_ID),
                eq(InteractionAction.FAILED), anyString(), eq("AUDIT_SIGNED_CASE_NO_MISSING"),
                eq("审核系统已签收但未返回案件号"));
        verifyNoInteractions(auditSignedUseCase);
    }

    @Test
    @DisplayName("签收处理失败：记录签收失败轨迹")
    void recover_signedRecordFailed_recordsFailedTrace() {
        ClaimApplication claimApplication = application(CLAIM_APPLICATION_NO, TENANT_CODE);
        when(claimApplicationRepository.findByStatusUpdatedBefore(eq(ClaimApplicationStatus.WAIT_AUDIT), any(),
                eq(BATCH_SIZE))).thenReturn(List.of(claimApplication));
        when(auditSystemPort.querySignedStatus(CLAIM_APPLICATION_NO))
                .thenReturn(new AuditSignedStatus.Signed(List.of("CASE-001")));
        org.mockito.Mockito.doThrow(new IllegalStateException("签收事务冲突"))
                .when(auditSignedUseCase).record(any());

        handler.recover();

        verify(compensationTransactionService).recordAttempt(eq(CLAIM_APPLICATION_NO),
                eq(InteractionType.AUDIT_SIGNED), eq(INTERACTION_NO), eq(REQUEST_ID),
                eq(InteractionAction.FAILED), anyString(), eq("AUDIT_SIGNED_RECORD_FAILED"), eq("签收事务冲突"));
    }

    @Test
    @DisplayName("单条失败不影响同批：前一条查询失败，后一条仍完成签收")
    void recover_singleItemFailure_doesNotStopBatch() {
        ClaimApplication failedApplication = application(CLAIM_APPLICATION_NO, TENANT_CODE);
        ClaimApplication succeededApplication = application("A2026110300000002", TENANT_CODE);
        when(claimApplicationRepository.findByStatusUpdatedBefore(eq(ClaimApplicationStatus.WAIT_AUDIT), any(),
                eq(BATCH_SIZE))).thenReturn(List.of(failedApplication, succeededApplication));
        when(auditSystemPort.querySignedStatus(CLAIM_APPLICATION_NO))
                .thenThrow(new IllegalStateException("审核系统超时"));
        when(auditSystemPort.querySignedStatus("A2026110300000002"))
                .thenReturn(new AuditSignedStatus.Signed(List.of("CASE-002")));

        int compensatedCount = handler.recover();

        assertThat(compensatedCount).isEqualTo(2);
        verify(compensationTransactionService).recordAttempt(eq(CLAIM_APPLICATION_NO),
                eq(InteractionType.AUDIT_SIGNED), anyString(), anyString(), eq(InteractionAction.FAILED),
                anyString(), eq("AUDIT_SIGNED_QUERY_FAILED"), anyString());
        ArgumentCaptor<AuditSignedCommand> commandCaptor = ArgumentCaptor.forClass(AuditSignedCommand.class);
        verify(auditSignedUseCase).record(commandCaptor.capture());
        assertThat(commandCaptor.getValue().claimApplicationNo()).isEqualTo("A2026110300000002");
        assertThat(commandCaptor.getValue().caseNos()).containsExactly("CASE-002");
    }

    private ClaimApplication application(String claimApplicationNo, String tenantCode) {
        ClaimApplication claimApplication = mock(ClaimApplication.class);
        when(claimApplication.getClaimApplicationNo()).thenReturn(claimApplicationNo);
        when(claimApplication.getTenantCode()).thenReturn(tenantCode);
        return claimApplication;
    }
}
