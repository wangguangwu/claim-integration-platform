package com.wangguangwu.claim.application.usecase.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.usecase.integration.consumption.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.usecase.audit.command.AuditSignedCommand;
import com.wangguangwu.claim.application.usecase.audit.result.AuditSignedOutcome;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 审核签收用例单元测试：验证判重、申请存在性校验与事务委托。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("审核签收用例")
class AuditSignedUseCaseTest {

    private static final String CLAIM_APPLICATION_NO = "A2026110200000001";
    private static final String TENANT_CODE = "tenant-a";
    private static final String EVENT_ID = "evt-018-001";
    private static final String TRACE_ID = "trace-018-001";

    private ClaimApplicationRepository claimApplicationRepository;
    private ConsumedEventRepository consumedEventRepository;
    private AuditSignedTransactionService transactionService;

    private AuditSignedUseCase useCase;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        consumedEventRepository = mock(ConsumedEventRepository.class);
        transactionService = mock(AuditSignedTransactionService.class);

        useCase = new AuditSignedUseCase(claimApplicationRepository, consumedEventRepository, transactionService);
    }

    @Test
    @DisplayName("重复事件：已消费直接跳过，不查申请、不写库")
    void record_duplicateEvent_skipsWithoutQueryingApplication() {
        when(consumedEventRepository.exists(AuditSignedTransactionService.CONSUMER_GROUP, EVENT_ID)).thenReturn(true);

        AuditSignedOutcome outcome = useCase.record(command());

        assertThat(outcome).isEqualTo(AuditSignedOutcome.SKIPPED);
        verify(claimApplicationRepository, never()).findByClaimApplicationNo(any());
        verify(transactionService, never()).record(any());
    }

    @Test
    @DisplayName("申请不存在：抛 CLAIM_APPLICATION_NOT_FOUND，异常信息包含业务标识")
    void record_applicationNotFound_throwsBusinessException() {
        when(consumedEventRepository.exists(AuditSignedTransactionService.CONSUMER_GROUP, EVENT_ID)).thenReturn(false);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.record(command()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationAsyncErrorCode.CLAIM_APPLICATION_NOT_FOUND)
                .hasMessageContaining(CLAIM_APPLICATION_NO)
                .hasMessageContaining(EVENT_ID);

        verify(transactionService, never()).record(any());
    }

    @Test
    @DisplayName("申请存在：委托事务服务并返回处理结果")
    void record_applicationExists_delegatesToTransactionService() {
        when(consumedEventRepository.exists(AuditSignedTransactionService.CONSUMER_GROUP, EVENT_ID)).thenReturn(false);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(mock(ClaimApplication.class)));
        when(transactionService.record(any())).thenReturn(AuditSignedOutcome.RECORDED);

        AuditSignedOutcome outcome = useCase.record(command());

        assertThat(outcome).isEqualTo(AuditSignedOutcome.RECORDED);
        verify(transactionService).record(any());
    }

    private AuditSignedCommand command() {
        return new AuditSignedCommand(CLAIM_APPLICATION_NO, TENANT_CODE, EVENT_ID, TRACE_ID,
                List.of("CASE-001", "CASE-002"));
    }
}
