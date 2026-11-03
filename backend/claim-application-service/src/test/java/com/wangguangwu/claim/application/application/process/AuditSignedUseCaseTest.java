package com.wangguangwu.claim.application.application.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.application.command.AuditSignedCommand;
import com.wangguangwu.claim.application.application.port.ConsumedEventRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 审核签收用例单元测试：验证消费登记预判与事务服务委托。
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

    private ConsumedEventRepository consumedEventRepository;
    private AuditSignedTransactionService transactionService;

    private AuditSignedUseCase useCase;

    @BeforeEach
    void setUp() {
        consumedEventRepository = mock(ConsumedEventRepository.class);
        transactionService = mock(AuditSignedTransactionService.class);

        useCase = new AuditSignedUseCase(consumedEventRepository, transactionService);
    }

    @Test
    @DisplayName("重复事件：已消费直接跳过，不委托事务服务")
    void record_duplicateEvent_skipsWithoutDelegating() {
        when(consumedEventRepository.exists(AuditSignedTransactionService.CONSUMER_GROUP, EVENT_ID)).thenReturn(true);

        AuditSignedOutcome outcome = useCase.record(command());

        assertThat(outcome).isEqualTo(AuditSignedOutcome.SKIPPED);
        verify(transactionService, never()).record(any());
    }

    @Test
    @DisplayName("未消费事件：委托事务服务并返回处理结果")
    void record_newEvent_delegatesToTransactionService() {
        when(consumedEventRepository.exists(AuditSignedTransactionService.CONSUMER_GROUP, EVENT_ID)).thenReturn(false);
        when(transactionService.record(any())).thenReturn(AuditSignedOutcome.COMPLETED);

        AuditSignedOutcome outcome = useCase.record(command());

        assertThat(outcome).isEqualTo(AuditSignedOutcome.COMPLETED);
        verify(transactionService).record(any());
    }

    private AuditSignedCommand command() {
        return new AuditSignedCommand(CLAIM_APPLICATION_NO, TENANT_CODE, EVENT_ID, TRACE_ID,
                List.of("CASE-001", "CASE-002"));
    }
}
