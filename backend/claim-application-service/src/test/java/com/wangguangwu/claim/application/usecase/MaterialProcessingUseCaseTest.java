package com.wangguangwu.claim.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.application.Application;
import com.wangguangwu.claim.application.domain.application.ApplicationRepository;
import com.wangguangwu.claim.application.domain.application.ApplicationStatus;
import com.wangguangwu.claim.application.domain.consumption.ConsumedEventRepository;
import com.wangguangwu.claim.application.domain.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.material.FileUploadResult;
import com.wangguangwu.claim.application.domain.material.Material;
import com.wangguangwu.claim.application.domain.material.MaterialFileProcessor;
import com.wangguangwu.claim.application.domain.material.MaterialRepository;
import com.wangguangwu.claim.application.usecase.command.MaterialProcessCommand;
import com.wangguangwu.claim.application.usecase.result.MaterialProcessOutcome;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 材料处理用例测试：重复消费、陈旧事件、成功与失败分流。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class MaterialProcessingUseCaseTest {

    private static final String APPLICATION_NO = "A2026102800000001";
    private static final String TENANT_CODE = "demo-insurer-a";
    private static final String EVENT_ID = "event-1";
    private static final String TRACE_ID = "trace-1";
    private static final String PRIMARY_INSURED_JSON = "{\"certificateNo\":\"110101199001010011\"}";

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final ConsumedEventRepository consumedEventRepository = mock(ConsumedEventRepository.class);
    private final MaterialRepository materialRepository = mock(MaterialRepository.class);
    private final MaterialFileProcessor materialFileProcessor = mock(MaterialFileProcessor.class);
    private final MaterialProcessingTransactionService transactionService =
            mock(MaterialProcessingTransactionService.class);
    private final InteractionLogRepository interactionLogRepository = mock(InteractionLogRepository.class);
    private final UnderwritingUseCase underwritingUseCase = mock(UnderwritingUseCase.class);
    private final ApplicationLifecycleRecordFactory recordFactory =
            new ApplicationLifecycleRecordFactory(new ObjectMapper());

    private MaterialProcessingUseCase materialProcessingUseCase;

    /**
     * 初始化待测用例。
     */
    @BeforeEach
    void setUp() {
        materialProcessingUseCase = new MaterialProcessingUseCase(applicationRepository, consumedEventRepository,
                materialRepository, materialFileProcessor, transactionService, recordFactory,
                interactionLogRepository, underwritingUseCase);
    }

    /**
     * 已消费的事件不再读取申请，也不处理材料。
     */
    @Test
    void shouldSkipAlreadyConsumedEvent() {
        when(consumedEventRepository.exists(MaterialProcessingTransactionService.CONSUMER_GROUP, EVENT_ID))
                .thenReturn(true);

        materialProcessingUseCase.process(command());

        verifyNoInteractions(applicationRepository, materialRepository, materialFileProcessor, transactionService,
                interactionLogRepository, underwritingUseCase);
    }

    /**
     * 状态或尝试次数不匹配的事件只记录复用轨迹，不发起承保分流。
     */
    @Test
    void shouldRecordReusedForStaleEvent() {
        Application application = acceptedApplication();
        when(applicationRepository.findByApplicationNo(APPLICATION_NO)).thenReturn(Optional.of(application));
        when(transactionService.recordReused(any(), anyString())).thenReturn(MaterialProcessOutcome.REUSED);

        materialProcessingUseCase.process(command());

        verify(transactionService).recordReused(any(), anyString());
        verifyNoInteractions(materialRepository, materialFileProcessor, underwritingUseCase);
        verify(interactionLogRepository).insert(any());
    }

    /**
     * 全部材料处理成功时写入受理结果并继续承保分流。
     */
    @Test
    void shouldAcceptAndDispatchUnderwritingWhenAllMaterialsSucceed() {
        Application application = pendingApplication();
        Material material = Material.createPending(APPLICATION_NO, "B20261028batch", "medical-record",
                "https://tenant.example.com/medical-record.pdf");
        when(applicationRepository.findByApplicationNo(APPLICATION_NO)).thenReturn(Optional.of(application));
        when(materialRepository.findByApplicationNoAndDeletedFalse(APPLICATION_NO)).thenReturn(List.of(material));
        when(materialFileProcessor.upload(any())).thenReturn(FileUploadResult.succeeded("mock://file-system/uploaded"));
        when(transactionService.recordProcessed(any(), anyString(), any(), anyList(), anyBoolean(), any(), any()))
                .thenReturn(MaterialProcessOutcome.ACCEPTED);

        materialProcessingUseCase.process(command());

        verify(transactionService).recordProcessed(any(), anyString(), any(), anyList(), eq(true), isNull(), isNull());
        verify(underwritingUseCase).dispatch(APPLICATION_NO, TRACE_ID);
    }

    /**
     * 任一材料被文件系统拒绝时推进受理失败，不发起承保分流。
     */
    @Test
    void shouldFailAcceptWhenAnyMaterialRejected() {
        Application application = pendingApplication();
        Material material = Material.createPending(APPLICATION_NO, "B20261028batch", "MATERIAL_TYPE_REJECTED",
                "https://tenant.example.com/rejected.pdf");
        when(applicationRepository.findByApplicationNo(APPLICATION_NO)).thenReturn(Optional.of(application));
        when(materialRepository.findByApplicationNoAndDeletedFalse(APPLICATION_NO)).thenReturn(List.of(material));
        when(materialFileProcessor.upload(any())).thenReturn(
                FileUploadResult.failed("MOCK_UPLOAD_REJECTED", "模拟文件系统拒绝上传"));
        when(transactionService.recordProcessed(any(), anyString(), any(), anyList(), anyBoolean(), any(), any()))
                .thenReturn(MaterialProcessOutcome.ACCEPT_FAILED);

        materialProcessingUseCase.process(command());

        verify(transactionService).recordProcessed(any(), anyString(), any(), anyList(), eq(false),
                eq("MOCK_UPLOAD_REJECTED"), eq("模拟文件系统拒绝上传"));
        verify(underwritingUseCase, never()).dispatch(anyString(), anyString());
    }

    /**
     * 文件系统不可用时异常向上抛出，交由 binder 重投，不写入任何业务结果。
     */
    @Test
    void shouldPropagateFileSystemFailureWithoutRecordingResult() {
        Application application = pendingApplication();
        Material material = Material.createPending(APPLICATION_NO, "B20261028batch", "medical-record",
                "https://tenant.example.com/medical-record.pdf");
        when(applicationRepository.findByApplicationNo(APPLICATION_NO)).thenReturn(Optional.of(application));
        when(materialRepository.findByApplicationNoAndDeletedFalse(APPLICATION_NO)).thenReturn(List.of(material));
        when(materialFileProcessor.upload(any())).thenThrow(new IllegalStateException("文件系统不可用"));

        assertThatThrownBy(() -> materialProcessingUseCase.process(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文件系统不可用");
        verify(transactionService, never()).recordProcessed(any(), anyString(), any(), anyList(), anyBoolean(), any(),
                any());
        verify(underwritingUseCase, never()).dispatch(anyString(), anyString());
    }

    /**
     * 事件对应的申请不存在属于数据一致性问题，直接抛出交由重试与人工排查。
     */
    @Test
    void shouldFailWhenApplicationMissing() {
        when(applicationRepository.findByApplicationNo(APPLICATION_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materialProcessingUseCase.process(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(APPLICATION_NO);
    }

    private MaterialProcessCommand command() {
        return new MaterialProcessCommand(APPLICATION_NO, TENANT_CODE, EVENT_ID, TRACE_ID, 1);
    }

    private Application pendingApplication() {
        return Application.createPending(TENANT_CODE, APPLICATION_NO, "EXT-001", "{}", PRIMARY_INSURED_JSON, "{}",
                "{}");
    }

    private Application acceptedApplication() {
        Application application = pendingApplication();
        application.setStatus(ApplicationStatus.ACCEPTED);
        return application;
    }
}
