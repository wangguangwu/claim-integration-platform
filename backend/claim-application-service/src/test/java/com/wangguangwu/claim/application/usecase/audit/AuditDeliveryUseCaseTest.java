package com.wangguangwu.claim.application.usecase.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.usecase.audit.command.AuditDeliveryCommand;
import com.wangguangwu.claim.application.usecase.audit.port.ServiceAuditSystemPort;
import com.wangguangwu.claim.application.usecase.audit.result.AuditDeliveryOutcome;
import com.wangguangwu.claim.application.usecase.integration.consumption.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 审核交付用例单元测试：验证事件判重与异常处理。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@DisplayName("审核交付用例")
class AuditDeliveryUseCaseTest {

    private static final String CLAIM_APPLICATION_NO = "A2026103100000001";
    private static final String TENANT_CODE = "tenant-a";
    private static final String EVENT_ID = "evt-001";
    private static final String TRACE_ID = "trace-001";

    private ClaimApplicationRepository claimApplicationRepository;
    private ConsumedEventRepository consumedEventRepository;
    private ServiceAuditSystemPort serviceAuditSystemPort;
    private AuditDeliveryTransactionService transactionService;
    private ClaimApplicationLifecycleRecordFactory recordFactory;

    private AuditDeliveryUseCase useCase;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        consumedEventRepository = mock(ConsumedEventRepository.class);
        serviceAuditSystemPort = mock(ServiceAuditSystemPort.class);
        transactionService = mock(AuditDeliveryTransactionService.class);
        recordFactory = mock(ClaimApplicationLifecycleRecordFactory.class);

        useCase = new AuditDeliveryUseCase(claimApplicationRepository, consumedEventRepository, serviceAuditSystemPort,
                transactionService, recordFactory);

        when(recordFactory.newInteractionNo()).thenReturn("INT-001");
    }

    @Test
    @DisplayName("首次到达：判重未消费 → 找到申请 → 状态匹配 → 调审核系统 → 事务内写轨迹 → 返回 DELIVERED")
    void deliver_firstTime_deliversToAuditSystem() {
        AuditDeliveryCommand command = new AuditDeliveryCommand(CLAIM_APPLICATION_NO, TENANT_CODE, EVENT_ID, TRACE_ID);
        when(consumedEventRepository.exists(AuditDeliveryTransactionService.CONSUMER_GROUP, EVENT_ID)).thenReturn(false);
        ClaimApplication claimApplication = mockClaimApplication(ClaimApplicationStatus.WAIT_AUDIT);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(claimApplication));
        when(transactionService.recordDelivered(command, "INT-001")).thenReturn(AuditDeliveryOutcome.DELIVERED);

        useCase.deliver(command);

        verify(serviceAuditSystemPort).deliver(any());
        verify(transactionService).recordDelivered(command, "INT-001");
    }

    @Test
    @DisplayName("重复到达：判重已消费 → 跳过处理，不调审核系统、不写轨迹")
    void deliver_duplicateEvent_skipsProcessing() {
        AuditDeliveryCommand command = new AuditDeliveryCommand(CLAIM_APPLICATION_NO, TENANT_CODE, EVENT_ID, TRACE_ID);
        when(consumedEventRepository.exists(AuditDeliveryTransactionService.CONSUMER_GROUP, EVENT_ID)).thenReturn(true);

        useCase.deliver(command);

        verify(claimApplicationRepository, never()).findByClaimApplicationNo(any());
        verify(serviceAuditSystemPort, never()).deliver(any());
        verify(transactionService, never()).recordDelivered(any(), any());
    }

    @Test
    @DisplayName("找不到申请：判重未消费 → 找不到申请 → 抛业务异常，异常信息包含业务标识")
    void deliver_applicationNotFound_throwsBusinessException() {
        AuditDeliveryCommand command = new AuditDeliveryCommand(CLAIM_APPLICATION_NO, TENANT_CODE, EVENT_ID, TRACE_ID);
        when(consumedEventRepository.exists(AuditDeliveryTransactionService.CONSUMER_GROUP, EVENT_ID)).thenReturn(false);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.deliver(command))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationIntakeErrorCode.AUDIT_DELIVERY_APPLICATION_NOT_FOUND)
                .hasMessageContaining(CLAIM_APPLICATION_NO)
                .hasMessageContaining(EVENT_ID);

        verify(serviceAuditSystemPort, never()).deliver(any());
        verify(transactionService, never()).recordDelivered(any(), any());
    }

    @Test
    @DisplayName("状态不匹配：判重未消费 → 找到申请 → 状态非 WAIT_AUDIT → 按重复事件处理 → 返回 REUSED")
    void deliver_statusMismatch_recordsReused() {
        AuditDeliveryCommand command = new AuditDeliveryCommand(CLAIM_APPLICATION_NO, TENANT_CODE, EVENT_ID, TRACE_ID);
        when(consumedEventRepository.exists(AuditDeliveryTransactionService.CONSUMER_GROUP, EVENT_ID)).thenReturn(false);
        ClaimApplication claimApplication = mockClaimApplication(ClaimApplicationStatus.ACCEPTED);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(claimApplication));
        when(transactionService.recordReused(command, "INT-001")).thenReturn(AuditDeliveryOutcome.REUSED);

        useCase.deliver(command);

        verify(serviceAuditSystemPort, never()).deliver(any());
        verify(transactionService).recordReused(command, "INT-001");
    }

    private ClaimApplication mockClaimApplication(ClaimApplicationStatus status) {
        ClaimApplication claimApplication = mock(ClaimApplication.class);
        when(claimApplication.isAwaitingAudit()).thenReturn(status == ClaimApplicationStatus.WAIT_AUDIT);
        when(claimApplication.getStatus()).thenReturn(status);
        return claimApplication;
    }
}
