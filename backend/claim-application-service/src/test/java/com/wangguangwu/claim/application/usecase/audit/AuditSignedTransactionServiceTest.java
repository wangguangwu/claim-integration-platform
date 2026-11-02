package com.wangguangwu.claim.application.usecase.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimCaseRelation;
import com.wangguangwu.claim.application.domain.claim.port.ClaimCaseRelationRepository;
import com.wangguangwu.claim.application.usecase.integration.consumption.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionLog;
import com.wangguangwu.claim.application.usecase.integration.interaction.port.InteractionLogRepository;
import com.wangguangwu.claim.application.usecase.audit.command.AuditSignedCommand;
import com.wangguangwu.claim.application.usecase.audit.result.AuditSignedOutcome;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 审核签收事务服务单元测试：验证消费登记、案件号补插与轨迹写入。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("审核签收事务服务")
class AuditSignedTransactionServiceTest {

    private static final String CLAIM_APPLICATION_NO = "A2026110200000001";
    private static final String TENANT_CODE = "tenant-a";
    private static final String EVENT_ID = "evt-018-001";
    private static final String TRACE_ID = "trace-018-001";

    private ConsumedEventRepository consumedEventRepository;
    private ClaimCaseRelationRepository claimCaseRelationRepository;
    private InteractionLogRepository interactionLogRepository;
    private ClaimApplicationLifecycleRecordFactory recordFactory;

    private AuditSignedTransactionService transactionService;

    @BeforeEach
    void setUp() {
        consumedEventRepository = mock(ConsumedEventRepository.class);
        claimCaseRelationRepository = mock(ClaimCaseRelationRepository.class);
        interactionLogRepository = mock(InteractionLogRepository.class);
        recordFactory = mock(ClaimApplicationLifecycleRecordFactory.class);

        transactionService = new AuditSignedTransactionService(consumedEventRepository, claimCaseRelationRepository,
                interactionLogRepository, recordFactory);

        when(recordFactory.interaction(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(InteractionLog.class));
    }

    @Test
    @DisplayName("首次签收：登记消费、插入全部案件号、写入开始与结束轨迹")
    void record_firstTime_insertsAllCaseNos() {
        when(consumedEventRepository.register(AuditSignedTransactionService.CONSUMER_GROUP, EVENT_ID,
                CLAIM_APPLICATION_NO)).thenReturn(true);
        when(claimCaseRelationRepository.findCaseNos(CLAIM_APPLICATION_NO)).thenReturn(List.of());

        AuditSignedOutcome outcome = transactionService.record(command(List.of("CASE-001", "CASE-002")));

        assertThat(outcome).isEqualTo(AuditSignedOutcome.RECORDED);
        List<ClaimCaseRelation> inserted = captureInsertedRelations();
        assertThat(inserted).extracting(ClaimCaseRelation::getCaseNo).containsExactly("CASE-001", "CASE-002");
        verify(interactionLogRepository, times(2)).insert(any());
    }

    @Test
    @DisplayName("部分案件号已存在：只补插缺失项")
    void record_partialExisting_insertsOnlyMissingCaseNos() {
        when(consumedEventRepository.register(AuditSignedTransactionService.CONSUMER_GROUP, EVENT_ID,
                CLAIM_APPLICATION_NO)).thenReturn(true);
        when(claimCaseRelationRepository.findCaseNos(CLAIM_APPLICATION_NO)).thenReturn(List.of("CASE-001"));

        AuditSignedOutcome outcome = transactionService.record(command(List.of("CASE-001", "CASE-002")));

        assertThat(outcome).isEqualTo(AuditSignedOutcome.RECORDED);
        List<ClaimCaseRelation> inserted = captureInsertedRelations();
        assertThat(inserted).extracting(ClaimCaseRelation::getCaseNo).containsExactly("CASE-002");
    }

    @Test
    @DisplayName("全部案件号已存在：不插入，按复用记录轨迹")
    void record_allExisting_returnsReused() {
        when(consumedEventRepository.register(AuditSignedTransactionService.CONSUMER_GROUP, EVENT_ID,
                CLAIM_APPLICATION_NO)).thenReturn(true);
        when(claimCaseRelationRepository.findCaseNos(CLAIM_APPLICATION_NO))
                .thenReturn(List.of("CASE-001", "CASE-002"));

        AuditSignedOutcome outcome = transactionService.record(command(List.of("CASE-001", "CASE-002")));

        assertThat(outcome).isEqualTo(AuditSignedOutcome.REUSED);
        assertThat(captureInsertedRelations()).isEmpty();
        verify(interactionLogRepository, times(2)).insert(any());
    }

    @Test
    @DisplayName("重复事件：消费登记已存在，直接跳过且不写任何数据")
    void record_duplicateEvent_returnsSkippedWithoutWrites() {
        when(consumedEventRepository.register(AuditSignedTransactionService.CONSUMER_GROUP, EVENT_ID,
                CLAIM_APPLICATION_NO)).thenReturn(false);

        AuditSignedOutcome outcome = transactionService.record(command(List.of("CASE-001")));

        assertThat(outcome).isEqualTo(AuditSignedOutcome.SKIPPED);
        verify(claimCaseRelationRepository, never()).findCaseNos(anyString());
        verify(claimCaseRelationRepository, never()).insertAll(any());
        verify(interactionLogRepository, never()).insert(any());
    }

    private AuditSignedCommand command(List<String> caseNos) {
        return new AuditSignedCommand(CLAIM_APPLICATION_NO, TENANT_CODE, EVENT_ID, TRACE_ID, caseNos);
    }

    @SuppressWarnings("unchecked")
    private List<ClaimCaseRelation> captureInsertedRelations() {
        ArgumentCaptor<List<ClaimCaseRelation>> captor = ArgumentCaptor.forClass(List.class);
        verify(claimCaseRelationRepository).insertAll(captor.capture());
        return captor.getValue();
    }
}
