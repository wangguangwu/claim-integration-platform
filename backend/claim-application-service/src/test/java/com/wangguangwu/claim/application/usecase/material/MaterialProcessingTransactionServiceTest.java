package com.wangguangwu.claim.application.usecase.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationErrorCode;
import com.wangguangwu.claim.application.domain.claim.material.Material;
import com.wangguangwu.claim.application.domain.claim.material.MaterialProcessingResult;
import com.wangguangwu.claim.application.domain.claim.material.MaterialStatus;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.exception.DomainException;
import com.wangguangwu.claim.application.usecase.integration.consumption.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionAction;
import com.wangguangwu.claim.application.usecase.integration.interaction.port.InteractionLogRepository;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.material.command.MaterialProcessCommand;
import com.wangguangwu.claim.application.usecase.material.result.MaterialProcessOutcome;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 材料处理事务服务单元测试：验证消费去重、版本过期、结果推进与写入失败向上抛出。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@DisplayName("材料处理事务服务")
class MaterialProcessingTransactionServiceTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261103-001";
    private static final String CLAIM_APPLICATION_NO = "A2026110300000001";
    private static final String BATCH_NO = "B2026110300000001";
    private static final String EVENT_ID = "event-001";
    private static final String TRACE_ID = "trace-001";
    private static final String INTERACTION_NO = "INT-001";
    private static final long MATERIAL_ID = 10L;

    private ConsumedEventRepository consumedEventRepository;
    private ClaimApplicationRepository claimApplicationRepository;
    private InteractionLogRepository interactionLogRepository;

    private MaterialProcessingTransactionService transactionService;

    @BeforeEach
    void setUp() {
        consumedEventRepository = mock(ConsumedEventRepository.class);
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        interactionLogRepository = mock(InteractionLogRepository.class);
        ClaimApplicationLifecycleRecordFactory recordFactory = new ClaimApplicationLifecycleRecordFactory(
                new ObjectMapper());

        transactionService = new MaterialProcessingTransactionService(consumedEventRepository,
                claimApplicationRepository, interactionLogRepository, recordFactory);

        when(consumedEventRepository.register(MaterialProcessingTransactionService.CONSUMER_GROUP, EVENT_ID,
                CLAIM_APPLICATION_NO)).thenReturn(true);
    }

    @Test
    @DisplayName("同一事件已登记：跳过且不读取聚合")
    void recordProcessed_duplicateEvent_skips() {
        when(consumedEventRepository.register(MaterialProcessingTransactionService.CONSUMER_GROUP, EVENT_ID,
                CLAIM_APPLICATION_NO)).thenReturn(false);

        MaterialProcessOutcome outcome = transactionService.recordProcessed(command(1), INTERACTION_NO,
                List.of(succeeded()), null, null);

        assertThat(outcome).isEqualTo(MaterialProcessOutcome.SKIPPED);
        verify(claimApplicationRepository, never()).findByClaimApplicationNo(anyString());
    }

    @Test
    @DisplayName("聚合保存版本过期：按重复事件返回且不写完成轨迹")
    void recordProcessed_versionConflict_returnsReused() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 1)));
        when(claimApplicationRepository.save(any(ClaimApplication.class))).thenReturn(false);

        MaterialProcessOutcome outcome = transactionService.recordProcessed(command(1), INTERACTION_NO,
                List.of(succeeded()), null, null);

        assertThat(outcome).isEqualTo(MaterialProcessOutcome.REUSED);
        verify(interactionLogRepository).insert(org.mockito.ArgumentMatchers.argThat(
                log -> InteractionAction.REUSED.name().equals(log.action())));
    }

    @Test
    @DisplayName("事务内尝试次数已变化：按重复事件返回且不保存聚合")
    void recordProcessed_attemptChanged_returnsReused() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 2)));

        MaterialProcessOutcome outcome = transactionService.recordProcessed(command(1), INTERACTION_NO,
                List.of(succeeded()), null, null);

        assertThat(outcome).isEqualTo(MaterialProcessOutcome.REUSED);
        verify(claimApplicationRepository, never()).save(any(ClaimApplication.class));
    }

    @Test
    @DisplayName("全部材料成功：保存聚合并返回已受理")
    void recordProcessed_allSucceeded_returnsAccepted() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 1)));
        when(claimApplicationRepository.save(any(ClaimApplication.class))).thenReturn(true);

        MaterialProcessOutcome outcome = transactionService.recordProcessed(command(1), INTERACTION_NO,
                List.of(succeeded()), null, null);

        assertThat(outcome).isEqualTo(MaterialProcessOutcome.ACCEPTED);
        verify(interactionLogRepository).insert(org.mockito.ArgumentMatchers.argThat(
                log -> InteractionAction.COMPLETED.name().equals(log.action())));
    }

    @Test
    @DisplayName("存在材料失败：保存聚合并返回受理失败，完成轨迹带错误码")
    void recordProcessed_withFailure_returnsAcceptFailed() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 1)));
        when(claimApplicationRepository.save(any(ClaimApplication.class))).thenReturn(true);

        MaterialProcessOutcome outcome = transactionService.recordProcessed(command(1), INTERACTION_NO,
                List.of(new MaterialProcessingResult(MATERIAL_ID, false, null)), "FILE_REJECTED", "文件格式不支持");

        assertThat(outcome).isEqualTo(MaterialProcessOutcome.ACCEPT_FAILED);
        verify(interactionLogRepository).insert(org.mockito.ArgumentMatchers.argThat(
                log -> InteractionAction.FAILED.name().equals(log.action())
                        && "FILE_REJECTED".equals(log.errorCode())
                        && "文件格式不支持".equals(log.errorMessage())));
    }

    @Test
    @DisplayName("材料结果缺失：聚合校验异常向上抛出以回滚事务")
    void recordProcessed_missingMaterialResult_propagatesException() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 1)));

        assertThatThrownBy(() -> transactionService.recordProcessed(command(1), INTERACTION_NO, List.of(), null, null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("缺失")
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_RESULT_INVALID);
        verify(claimApplicationRepository, never()).save(any(ClaimApplication.class));
    }

    @Test
    @DisplayName("交互轨迹写入失败：异常向上抛出以回滚聚合与消费登记")
    void recordProcessed_interactionWriteFails_propagatesException() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 1)));
        org.mockito.Mockito.doThrow(new IllegalStateException("交互轨迹写入失败"))
                .when(interactionLogRepository).insert(any());

        assertThatThrownBy(() -> transactionService.recordProcessed(command(1), INTERACTION_NO,
                List.of(succeeded()), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("交互轨迹写入失败");
        verify(claimApplicationRepository, never()).save(any(ClaimApplication.class));
    }

    @Test
    @DisplayName("聚合保存异常：异常向上抛出以回滚消费登记")
    void recordProcessed_saveFails_propagatesException() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 1)));
        when(claimApplicationRepository.save(any(ClaimApplication.class)))
                .thenThrow(new IllegalStateException("聚合保存失败"));

        assertThatThrownBy(() -> transactionService.recordProcessed(command(1), INTERACTION_NO,
                List.of(succeeded()), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("聚合保存失败");
    }

    @Test
    @DisplayName("消费登记异常：异常向上抛出且不写轨迹")
    void recordProcessed_registerFails_propagatesException() {
        when(consumedEventRepository.register(MaterialProcessingTransactionService.CONSUMER_GROUP, EVENT_ID,
                CLAIM_APPLICATION_NO)).thenThrow(new IllegalStateException("消费登记失败"));

        assertThatThrownBy(() -> transactionService.recordProcessed(command(1), INTERACTION_NO,
                List.of(succeeded()), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("消费登记失败");
        verify(interactionLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("重复事件登记轨迹：登记成功后写开始与复用两条")
    void recordReused_registersConsumptionAndRecordsInteraction() {
        MaterialProcessOutcome outcome = transactionService.recordReused(command(1), INTERACTION_NO);

        assertThat(outcome).isEqualTo(MaterialProcessOutcome.REUSED);
        verify(interactionLogRepository).insert(org.mockito.ArgumentMatchers.argThat(
                log -> InteractionAction.STARTED.name().equals(log.action())));
        verify(interactionLogRepository).insert(org.mockito.ArgumentMatchers.argThat(
                log -> InteractionAction.REUSED.name().equals(log.action())));
    }

    private MaterialProcessingResult succeeded() {
        return new MaterialProcessingResult(MATERIAL_ID, true, "https://f/1.pdf");
    }

    private MaterialProcessCommand command(int attempt) {
        return new MaterialProcessCommand(CLAIM_APPLICATION_NO, TENANT_CODE, EVENT_ID, TRACE_ID, attempt);
    }

    private ClaimApplication application(ClaimApplicationStatus status, int attempt) {
        Material material = Material.restore(MATERIAL_ID, BATCH_NO, "POLICY", "https://example.com/policy.pdf",
                null, MaterialStatus.PENDING_PROCESS, false, null);
        return ClaimApplication.restore(1L, TENANT_CODE, CLAIM_APPLICATION_NO, EXTERNAL_NO, status, attempt,
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                null,
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                0L, List.of(material), List.of());
    }
}
