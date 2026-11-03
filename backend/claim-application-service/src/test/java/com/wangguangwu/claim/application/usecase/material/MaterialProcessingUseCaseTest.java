package com.wangguangwu.claim.application.usecase.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.material.Material;
import com.wangguangwu.claim.application.domain.claim.material.MaterialProcessingResult;
import com.wangguangwu.claim.application.domain.claim.material.MaterialStatus;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.usecase.integration.consumption.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.material.command.MaterialProcessCommand;
import com.wangguangwu.claim.application.usecase.material.model.FileUploadResult;
import com.wangguangwu.claim.application.usecase.material.model.MaterialUploadRequest;
import com.wangguangwu.claim.application.usecase.material.port.MaterialFileProcessor;
import com.wangguangwu.claim.application.usecase.material.result.MaterialProcessOutcome;
import com.wangguangwu.claim.application.usecase.underwriting.UnderwritingUseCase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 材料处理用例单元测试：验证重复事件、过期消息、材料结果完整性、失败推进与承保分流。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@DisplayName("材料处理用例")
class MaterialProcessingUseCaseTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261103-001";
    private static final String CLAIM_APPLICATION_NO = "A2026110300000001";
    private static final String BATCH_NO = "B2026110300000001";
    private static final String EVENT_ID = "event-001";
    private static final String TRACE_ID = "trace-001";
    private static final long MATERIAL_ID = 10L;

    private ClaimApplicationRepository claimApplicationRepository;
    private ConsumedEventRepository consumedEventRepository;
    private MaterialFileProcessor materialFileProcessor;
    private MaterialProcessingTransactionService transactionService;
    private UnderwritingUseCase underwritingUseCase;

    private MaterialProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        consumedEventRepository = mock(ConsumedEventRepository.class);
        materialFileProcessor = mock(MaterialFileProcessor.class);
        transactionService = mock(MaterialProcessingTransactionService.class);
        underwritingUseCase = mock(UnderwritingUseCase.class);
        ClaimApplicationLifecycleRecordFactory recordFactory = mock(ClaimApplicationLifecycleRecordFactory.class);
        when(recordFactory.newInteractionNo()).thenReturn("INT-001");

        useCase = new MaterialProcessingUseCase(claimApplicationRepository, consumedEventRepository,
                materialFileProcessor, transactionService, recordFactory, underwritingUseCase);
    }

    @Test
    @DisplayName("事件已消费：直接跳过且不读取聚合")
    void process_consumedEvent_skips() {
        when(consumedEventRepository.exists(MaterialProcessingTransactionService.CONSUMER_GROUP, EVENT_ID))
                .thenReturn(true);

        useCase.process(command(1));

        verify(claimApplicationRepository, never()).findByClaimApplicationNo(anyString());
        verify(materialFileProcessor, never()).upload(any());
    }

    @Test
    @DisplayName("过期消息：受理尝试次数与当前申请不一致时按重复事件处理")
    void process_staleAttempt_recordsReused() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 2)));

        useCase.process(command(1));

        verify(transactionService).recordReused(any(MaterialProcessCommand.class), eq("INT-001"));
        verify(transactionService, never()).recordProcessed(any(), any(), any(), any(), any());
        verify(materialFileProcessor, never()).upload(any());
    }

    @Test
    @DisplayName("状态已不是待受理：按重复事件处理且不上传材料")
    void process_statusChanged_recordsReused() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.WAIT_AUDIT, 1)));

        useCase.process(command(1));

        verify(transactionService).recordReused(any(MaterialProcessCommand.class), eq("INT-001"));
        verify(materialFileProcessor, never()).upload(any());
    }

    @Test
    @DisplayName("全部材料上传成功：提交完整结果并触发承保分流")
    void process_allSucceeded_dispatchesUnderwriting() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 1)));
        when(materialFileProcessor.upload(any(MaterialUploadRequest.class)))
                .thenReturn(FileUploadResult.succeeded("https://f/1.pdf"));
        when(transactionService.recordProcessed(any(), any(), anyList(), eq(null), eq(null)))
                .thenReturn(MaterialProcessOutcome.ACCEPTED);

        useCase.process(command(1));

        ArgumentCaptor<List<MaterialProcessingResult>> resultsCaptor = resultsCaptor();
        verify(transactionService).recordProcessed(any(), eq("INT-001"), resultsCaptor.capture(), eq(null), eq(null));
        assertThat(resultsCaptor.getValue()).containsExactly(
                new MaterialProcessingResult(MATERIAL_ID, true, "https://f/1.pdf"));
        verify(underwritingUseCase).dispatch(CLAIM_APPLICATION_NO, TRACE_ID);
    }

    @Test
    @DisplayName("材料上传失败：记录失败错误码且不触发承保分流")
    void process_uploadFailed_recordsFailureCode() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 1)));
        when(materialFileProcessor.upload(any(MaterialUploadRequest.class)))
                .thenReturn(FileUploadResult.failed("FILE_REJECTED", "文件格式不支持"));
        when(transactionService.recordProcessed(any(), any(), anyList(), eq("FILE_REJECTED"), eq("文件格式不支持")))
                .thenReturn(MaterialProcessOutcome.ACCEPT_FAILED);

        useCase.process(command(1));

        verify(underwritingUseCase, never()).dispatch(anyString(), anyString());
    }

    @Test
    @DisplayName("材料上传失败且未给错误码：使用统一失败码")
    void process_uploadFailedWithoutCode_usesDefaultFailureCode() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO))
                .thenReturn(Optional.of(application(ClaimApplicationStatus.PENDING_ACCEPT, 1)));
        when(materialFileProcessor.upload(any(MaterialUploadRequest.class)))
                .thenReturn(new FileUploadResult(false, null, null, null));
        when(transactionService.recordProcessed(any(), any(), anyList(), eq("FILE_UPLOAD_FAILED"), eq(null)))
                .thenReturn(MaterialProcessOutcome.ACCEPT_FAILED);

        useCase.process(command(1));

        verify(transactionService).recordProcessed(any(), eq("INT-001"), anyList(), eq("FILE_UPLOAD_FAILED"), eq(null));
    }

    @Test
    @DisplayName("申请不存在：抛出异常交由消息重投")
    void process_applicationMissing_throwsException() {
        when(claimApplicationRepository.findByClaimApplicationNo(CLAIM_APPLICATION_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.process(command(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CLAIM_APPLICATION_NO);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<MaterialProcessingResult>> resultsCaptor() {
        return ArgumentCaptor.forClass(List.class);
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
