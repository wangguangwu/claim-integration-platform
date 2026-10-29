package com.wangguangwu.claim.application.usecase.integration.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.Gender;
import com.wangguangwu.claim.application.domain.claim.PersonInfo;
import com.wangguangwu.claim.application.domain.integration.compensation.ApplicationCompensationPolicy;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLog;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEventRepository;
import com.wangguangwu.claim.application.domain.underwriting.AutoUnderwritingRequester;
import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResult;
import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResultQuery;
import com.wangguangwu.claim.application.shared.contract.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.underwriting.UnderwritingCallbackUseCase;
import com.wangguangwu.claim.application.usecase.underwriting.UnderwritingUseCase;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 补偿用例测试：投递滞留重置、已受理滞留重试与承保中结果查询。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class ApplicationCompensationUseCaseTest {

    private static final String APPLICATION_NO = "A2026102800000001";
    private static final String TENANT_CODE = "tenant-a";
    private static final String CERTIFICATE_NO = "110101199001010011";

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final UnderwritingUseCase underwritingUseCase = mock(UnderwritingUseCase.class);
    private final UnderwritingCallbackUseCase underwritingCallbackUseCase = mock(UnderwritingCallbackUseCase.class);
    private final UnderwritingResultQuery underwritingResultQuery = mock(UnderwritingResultQuery.class);
    private final AutoUnderwritingRequester autoUnderwritingRequester = mock(AutoUnderwritingRequester.class);
    private final InteractionLogRepository interactionLogRepository = mock(InteractionLogRepository.class);
    private final ApplicationLifecycleRecordFactory recordFactory =
            new ApplicationLifecycleRecordFactory(new ObjectMapper());
    private final ApplicationCompensationPolicy compensationPolicy = mock(ApplicationCompensationPolicy.class);

    private ApplicationCompensationUseCase applicationCompensationUseCase;

    /**
     * 初始化待测补偿用例。
     */
    @BeforeEach
    void setUp() {
        when(compensationPolicy.batchSize()).thenReturn(20);
        when(compensationPolicy.outboxSendingTimeout()).thenReturn(Duration.ofSeconds(60));
        when(compensationPolicy.acceptedStallTimeout()).thenReturn(Duration.ofMinutes(5));
        when(compensationPolicy.underwritingTimeout()).thenReturn(Duration.ofMinutes(1));
        applicationCompensationUseCase = new ApplicationCompensationUseCase(outboxEventRepository,
                applicationRepository, underwritingUseCase, underwritingCallbackUseCase, underwritingResultQuery,
                autoUnderwritingRequester, recordFactory, interactionLogRepository, compensationPolicy);
    }

    /**
     * 发送滞留事件按配置阈值重置为待投递。
     */
    @Test
    void shouldResetStuckSendingEvents() {
        when(outboxEventRepository.resetStuckSending(any())).thenReturn(2);

        int resetCount = applicationCompensationUseCase.resetStuckSendingEvents();

        assertThat(resetCount).isEqualTo(2);
        ArgumentCaptor<LocalDateTime> sendingBeforeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxEventRepository).resetStuckSending(sendingBeforeCaptor.capture());
        assertThat(sendingBeforeCaptor.getValue()).isBefore(LocalDateTime.now().minusSeconds(30));
    }

    /**
     * 已受理滞留逐条重试分流，单条失败不影响同批其他申请。
     */
    @Test
    void shouldRedispatchEveryStalledAcceptedApplication() {
        ClaimApplication firstApplication = application(ApplicationStatus.ACCEPTED);
        ClaimApplication secondApplication = persisted("A2026102800000002", "EXT-002", ApplicationStatus.ACCEPTED);
        when(applicationRepository.findByStatusUpdatedBefore(eq(ApplicationStatus.ACCEPTED), any(), eq(20)))
                .thenReturn(List.of(firstApplication, secondApplication));
        doThrow(new IllegalStateException("保单查询异常")).when(underwritingUseCase)
                .dispatch(eq(APPLICATION_NO), anyString());

        int compensatedCount = applicationCompensationUseCase.redispatchAcceptedApplications();

        assertThat(compensatedCount).isEqualTo(2);
        verify(underwritingUseCase).dispatch(eq("A2026102800000002"), anyString());
    }

    /**
     * 承保结果为已完成时按补偿请求标识推进状态，幂等规则与回调一致。
     */
    @Test
    void shouldCompleteUnderwritingWhenResultCompleted() {
        when(applicationRepository.findByStatusUpdatedBefore(eq(ApplicationStatus.UNDERWRITING), any(), eq(20)))
                .thenReturn(List.of(application(ApplicationStatus.UNDERWRITING)));
        when(underwritingResultQuery.query(APPLICATION_NO)).thenReturn(UnderwritingResult.COMPLETED);

        applicationCompensationUseCase.recoverUnderwritingApplications();

        ArgumentCaptor<UnderwritingCompletedCommand> commandCaptor =
                ArgumentCaptor.forClass(UnderwritingCompletedCommand.class);
        verify(underwritingCallbackUseCase).complete(commandCaptor.capture());
        assertThat(commandCaptor.getValue().applicationNo()).isEqualTo(APPLICATION_NO);
        assertThat(commandCaptor.getValue().requestId()).isEqualTo("compensation-" + APPLICATION_NO);
        verify(autoUnderwritingRequester, never()).request(anyString(), anyString(), anyString());
    }

    /**
     * 承保系统无记录时重发自动承保请求，申请保持承保中状态。
     */
    @Test
    void shouldReRequestAutoUnderwritingWhenResultNotFound() {
        when(applicationRepository.findByStatusUpdatedBefore(eq(ApplicationStatus.UNDERWRITING), any(), eq(20)))
                .thenReturn(List.of(application(ApplicationStatus.UNDERWRITING)));
        when(underwritingResultQuery.query(APPLICATION_NO)).thenReturn(UnderwritingResult.NOT_FOUND);

        applicationCompensationUseCase.recoverUnderwritingApplications();

        verify(autoUnderwritingRequester).request(APPLICATION_NO, TENANT_CODE, CERTIFICATE_NO);
        verify(underwritingCallbackUseCase, never()).complete(any());
    }

    /**
     * 承保仍在处理中时只记录查询轨迹，不推进也不重发。
     */
    @Test
    void shouldKeepUnderwritingWhenResultProcessing() {
        when(applicationRepository.findByStatusUpdatedBefore(eq(ApplicationStatus.UNDERWRITING), any(), eq(20)))
                .thenReturn(List.of(application(ApplicationStatus.UNDERWRITING)));
        when(underwritingResultQuery.query(APPLICATION_NO)).thenReturn(UnderwritingResult.PROCESSING);

        applicationCompensationUseCase.recoverUnderwritingApplications();

        verify(underwritingCallbackUseCase, never()).complete(any());
        verify(autoUnderwritingRequester, never()).request(anyString(), anyString(), anyString());
        assertThat(capturedActions()).containsExactly(InteractionAction.STARTED, InteractionAction.COMPLETED);
    }

    /**
     * 承保结果查询失败时只记录失败轨迹，等待下一轮补偿。
     */
    @Test
    void shouldRecordFailureWhenUnderwritingResultQueryFails() {
        when(applicationRepository.findByStatusUpdatedBefore(eq(ApplicationStatus.UNDERWRITING), any(), eq(20)))
                .thenReturn(List.of(application(ApplicationStatus.UNDERWRITING)));
        when(underwritingResultQuery.query(APPLICATION_NO)).thenThrow(new IllegalStateException("承保系统超时"));

        applicationCompensationUseCase.recoverUnderwritingApplications();

        verify(underwritingCallbackUseCase, never()).complete(any());
        List<InteractionLog> logs = capturedLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(1).action()).isEqualTo(InteractionAction.FAILED.name());
        assertThat(logs.get(1).errorCode()).isEqualTo("UNDERWRITING_RESULT_QUERY_FAILED");
        verifyNoInteractions(autoUnderwritingRequester);
    }

    private List<InteractionLog> capturedLogs() {
        ArgumentCaptor<InteractionLog> logCaptor = ArgumentCaptor.forClass(InteractionLog.class);
        verify(interactionLogRepository, atLeastOnce()).insert(logCaptor.capture());
        return logCaptor.getAllValues();
    }

    private List<InteractionAction> capturedActions() {
        return capturedLogs().stream()
                .map(interactionLog -> InteractionAction.valueOf(interactionLog.action()))
                .toList();
    }

    private ClaimApplication application(ApplicationStatus status) {
        return persisted(APPLICATION_NO, "EXT-001", status);
    }

    private ClaimApplication persisted(String applicationNo, String externalApplicationNo,
                                       ApplicationStatus status) {
        PersonInfo person = new PersonInfo("张三", Gender.MALE, "ID_CARD", CERTIFICATE_NO);
        return ClaimApplication.restore(1L, TENANT_CODE, applicationNo, externalApplicationNo, status, 1,
                person, person, person, person);
    }
}
