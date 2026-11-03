package com.wangguangwu.claim.application.application.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimCaseRelation;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.port.ClaimCaseRelationRepository;
import com.wangguangwu.claim.application.domain.claim.AccidentPerson;
import com.wangguangwu.claim.application.domain.claim.Applicant;
import com.wangguangwu.claim.application.domain.claim.Gender;
import com.wangguangwu.claim.application.domain.claim.Payee;
import com.wangguangwu.claim.application.domain.claim.PrimaryInsured;
import com.wangguangwu.claim.application.application.exception.AuditSignedConflictException;
import com.wangguangwu.claim.application.application.exception.ClaimApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.application.command.AuditSignedCommand;
import com.wangguangwu.claim.application.application.exception.ApplicationException;
import com.wangguangwu.claim.application.application.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.application.port.model.InteractionAction;
import com.wangguangwu.claim.application.application.port.model.InteractionLog;
import com.wangguangwu.claim.application.application.port.InteractionLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 审核签收事务服务单元测试：验证完成迁移、关系追加、租户与状态校验、版本冲突与轨迹写入。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@DisplayName("审核签收事务服务")
class AuditSignedTransactionServiceTest {

    private static final String CLAIM_APPLICATION_NO = "A2026110200000001";
    private static final String EXTERNAL_NO = "EXT-20261102-001";
    private static final String TENANT_CODE = "tenant-a";
    private static final String EVENT_ID = "evt-018-001";
    private static final String TRACE_ID = "trace-018-001";

    private ConsumedEventRepository consumedEventRepository;
    private ClaimApplicationRepository claimApplicationRepository;
    private ClaimCaseRelationRepository claimCaseRelationRepository;
    private InteractionLogRepository interactionLogRepository;

    private AuditSignedTransactionService transactionService;

    @BeforeEach
    void setUp() {
        consumedEventRepository = mock(ConsumedEventRepository.class);
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        claimCaseRelationRepository = mock(ClaimCaseRelationRepository.class);
        interactionLogRepository = mock(InteractionLogRepository.class);

        transactionService = new AuditSignedTransactionService(consumedEventRepository, claimApplicationRepository,
                claimCaseRelationRepository, interactionLogRepository,
                new ClaimApplicationLifecycleRecordFactory(new ObjectMapper()));
    }

    @Test
    @DisplayName("等待审核签收：完成申请、插入全部案件号并写完成轨迹")
    void record_awaitingAudit_completesApplication() {
        givenRegistered(true);
        givenApplication(application(ClaimApplicationStatus.WAIT_AUDIT, List.of()));
        when(claimCaseRelationRepository.findCaseNos(CLAIM_APPLICATION_NO)).thenReturn(List.of());
        when(claimApplicationRepository.save(any())).thenReturn(true);

        AuditSignedOutcome outcome = transactionService.record(command(List.of("CASE-001", "CASE-002")));

        assertThat(outcome).isEqualTo(AuditSignedOutcome.COMPLETED);
        assertThat(captureInsertedRelations()).extracting(ClaimCaseRelation::getCaseNo)
                .containsExactly("CASE-001", "CASE-002");
        assertThat(captureInteractionActions()).containsExactly(InteractionAction.STARTED,
                InteractionAction.COMPLETED);
        ArgumentCaptor<ClaimApplication> saved = ArgumentCaptor.forClass(ClaimApplication.class);
        verify(claimApplicationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ClaimApplicationStatus.COMPLETED);
        assertThat(saved.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("等待审核签收：案件号全部已登记仍返回完成")
    void record_awaitingAuditWithoutNewCaseNos_stillCompletes() {
        givenRegistered(true);
        givenApplication(application(ClaimApplicationStatus.WAIT_AUDIT, List.of()));
        when(claimCaseRelationRepository.findCaseNos(CLAIM_APPLICATION_NO)).thenReturn(List.of("CASE-001"));
        when(claimApplicationRepository.save(any())).thenReturn(true);

        AuditSignedOutcome outcome = transactionService.record(command(List.of("CASE-001")));

        assertThat(outcome).isEqualTo(AuditSignedOutcome.COMPLETED);
        assertThat(captureInsertedRelations()).isEmpty();
        assertThat(captureInteractionActions()).containsExactly(InteractionAction.STARTED,
                InteractionAction.COMPLETED);
    }

    @Test
    @DisplayName("已完成申请：补充新案件号返回 RECORDED 且不保存聚合")
    void record_completedWithNewCaseNos_returnsRecorded() {
        givenRegistered(true);
        givenApplication(application(ClaimApplicationStatus.COMPLETED, List.of()));
        when(claimCaseRelationRepository.findCaseNos(CLAIM_APPLICATION_NO)).thenReturn(List.of("CASE-001"));

        AuditSignedOutcome outcome = transactionService.record(command(List.of("CASE-001", "CASE-002")));

        assertThat(outcome).isEqualTo(AuditSignedOutcome.RECORDED);
        assertThat(captureInsertedRelations()).extracting(ClaimCaseRelation::getCaseNo).containsExactly("CASE-002");
        verify(claimApplicationRepository, never()).save(any());
        assertThat(captureInteractionActions()).containsExactly(InteractionAction.STARTED,
                InteractionAction.COMPLETED);
    }

    @Test
    @DisplayName("已完成申请：案件号全部已登记返回 REUSED")
    void record_completedWithAllCaseNosExisting_returnsReused() {
        givenRegistered(true);
        givenApplication(application(ClaimApplicationStatus.COMPLETED, List.of()));
        when(claimCaseRelationRepository.findCaseNos(CLAIM_APPLICATION_NO))
                .thenReturn(List.of("CASE-001", "CASE-002"));

        AuditSignedOutcome outcome = transactionService.record(command(List.of("CASE-001", "CASE-002")));

        assertThat(outcome).isEqualTo(AuditSignedOutcome.REUSED);
        assertThat(captureInsertedRelations()).isEmpty();
        verify(claimApplicationRepository, never()).save(any());
        assertThat(captureInteractionActions()).containsExactly(InteractionAction.STARTED,
                InteractionAction.REUSED);
    }

    @Test
    @DisplayName("重复事件：消费登记已存在，直接跳过且不写任何数据")
    void record_duplicateEvent_returnsSkippedWithoutWrites() {
        givenRegistered(false);

        AuditSignedOutcome outcome = transactionService.record(command(List.of("CASE-001")));

        assertThat(outcome).isEqualTo(AuditSignedOutcome.SKIPPED);
        verify(claimApplicationRepository, never()).findByClaimApplicationNo(anyString());
        verify(claimCaseRelationRepository, never()).findCaseNos(anyString());
        verify(claimCaseRelationRepository, never()).insertAll(any());
        verify(interactionLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("申请不存在：抛 CLAIM_APPLICATION_NOT_FOUND，不写关系与轨迹")
    void record_applicationNotFound_throwsApplicationException() {
        givenRegistered(true);
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.record(command(List.of("CASE-001"))))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationAsyncErrorCode.CLAIM_APPLICATION_NOT_FOUND)
                .hasMessageContaining(CLAIM_APPLICATION_NO)
                .hasMessageContaining(EVENT_ID);
        verify(claimCaseRelationRepository, never()).insertAll(any());
        verify(interactionLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("租户不一致：抛 AUDIT_SIGNED_TENANT_MISMATCH，不写关系与轨迹")
    void record_tenantMismatch_throwsApplicationException() {
        givenRegistered(true);
        givenApplication(application(ClaimApplicationStatus.WAIT_AUDIT, List.of()));

        assertThatThrownBy(() -> transactionService.record(new AuditSignedCommand(CLAIM_APPLICATION_NO,
                "tenant-b", EVENT_ID, TRACE_ID, List.of("CASE-001"))))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_TENANT_MISMATCH)
                .hasMessageContaining(CLAIM_APPLICATION_NO);
        verify(claimCaseRelationRepository, never()).insertAll(any());
        verify(interactionLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("状态不合法：抛 AUDIT_SIGNED_STATUS_INVALID")
    void record_statusNotAcceptable_throwsApplicationException() {
        givenRegistered(true);
        givenApplication(application(ClaimApplicationStatus.ACCEPTED, List.of()));

        assertThatThrownBy(() -> transactionService.record(command(List.of("CASE-001"))))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_STATUS_INVALID)
                .hasMessageContaining("ACCEPTED");
        verify(claimApplicationRepository, never()).save(any());
        verify(interactionLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("等待审核存在未关闭问题件：抛 AUDIT_SIGNED_STATUS_INVALID")
    void record_awaitingAuditWithOpenIssue_throwsApplicationException() {
        givenRegistered(true);
        givenApplication(application(ClaimApplicationStatus.WAIT_AUDIT,
                List.of(ClaimApplicationIssue.create("ISSUE-001", "缺少发票", "auditor-1", "{}"))));

        assertThatThrownBy(() -> transactionService.record(command(List.of("CASE-001"))))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_STATUS_INVALID);
        verify(claimApplicationRepository, never()).save(any());
        verify(interactionLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("版本冲突：保存返回 false 时抛内部冲突异常")
    void record_versionConflict_throwsConflictException() {
        givenRegistered(true);
        givenApplication(application(ClaimApplicationStatus.WAIT_AUDIT, List.of()));
        when(claimCaseRelationRepository.findCaseNos(CLAIM_APPLICATION_NO)).thenReturn(List.of());
        when(claimApplicationRepository.save(any())).thenReturn(false);

        assertThatThrownBy(() -> transactionService.record(command(List.of("CASE-001"))))
                .isInstanceOf(AuditSignedConflictException.class)
                .hasMessageContaining(CLAIM_APPLICATION_NO)
                .hasMessageContaining(EVENT_ID);
        assertThat(captureInteractionActions()).containsExactly(InteractionAction.STARTED);
    }

    private void givenRegistered(boolean registered) {
        when(consumedEventRepository.register(AuditSignedTransactionService.CONSUMER_GROUP, EVENT_ID,
                CLAIM_APPLICATION_NO)).thenReturn(registered);
    }

    private void givenApplication(ClaimApplication claimApplication) {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(claimApplication));
    }

    private ClaimApplication application(ClaimApplicationStatus status, List<ClaimApplicationIssue> issues) {
        LocalDateTime completedAt = status == ClaimApplicationStatus.COMPLETED ? LocalDateTime.now() : null;
        return ClaimApplication.restore(1L, TENANT_CODE, CLAIM_APPLICATION_NO, EXTERNAL_NO, status, 1, completedAt,
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                new AccidentPerson("王五", Gender.MALE, "身份证", "110101199003033456"),
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                0L, List.of(), issues);
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

    private List<InteractionAction> captureInteractionActions() {
        ArgumentCaptor<InteractionLog> captor = ArgumentCaptor.forClass(InteractionLog.class);
        verify(interactionLogRepository, atLeastOnce()).insert(captor.capture());
        return captor.getAllValues().stream()
                .map(log -> InteractionAction.valueOf(log.action()))
                .toList();
    }
}
