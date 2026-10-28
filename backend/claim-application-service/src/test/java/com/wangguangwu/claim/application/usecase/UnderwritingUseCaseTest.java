package com.wangguangwu.claim.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.application.Application;
import com.wangguangwu.claim.application.domain.application.ApplicationRepository;
import com.wangguangwu.claim.application.domain.application.ApplicationStatus;
import com.wangguangwu.claim.application.domain.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.interaction.InteractionLog;
import com.wangguangwu.claim.application.domain.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.underwriting.AutoUnderwritingRequester;
import com.wangguangwu.claim.application.domain.underwriting.PolicySnapshot;
import com.wangguangwu.claim.application.domain.underwriting.ValidPolicyQuery;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 承保分流用例测试：保单存在、不存在、查询失败与状态守卫。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class UnderwritingUseCaseTest {

    private static final String APPLICATION_NO = "A2026102800000001";
    private static final String TENANT_CODE = "demo-insurer-a";
    private static final String CERTIFICATE_NO = "110101199001010011";
    private static final String TRACE_ID = "trace-1";

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final ApplicationPersonReader applicationPersonReader = mock(ApplicationPersonReader.class);
    private final ValidPolicyQuery validPolicyQuery = mock(ValidPolicyQuery.class);
    private final AutoUnderwritingRequester autoUnderwritingRequester = mock(AutoUnderwritingRequester.class);
    private final UnderwritingTransactionService transactionService = mock(UnderwritingTransactionService.class);
    private final InteractionLogRepository interactionLogRepository = mock(InteractionLogRepository.class);
    private final ApplicationLifecycleRecordFactory recordFactory =
            new ApplicationLifecycleRecordFactory(new ObjectMapper());

    private UnderwritingUseCase underwritingUseCase;

    /**
     * 初始化待测用例。
     */
    @BeforeEach
    void setUp() {
        underwritingUseCase = new UnderwritingUseCase(applicationRepository, applicationPersonReader,
                validPolicyQuery, autoUnderwritingRequester, transactionService, recordFactory,
                interactionLogRepository);
        when(applicationRepository.findByApplicationNo(APPLICATION_NO)).thenReturn(Optional.of(acceptedApplication()));
        when(applicationPersonReader.readPrimaryInsuredCertificateNo(any())).thenReturn(CERTIFICATE_NO);
    }

    /**
     * 存在有效保单时直接推进等待审核签收，不发起自动承保。
     */
    @Test
    void shouldPromoteToWaitAuditWhenValidPolicyExists() {
        when(validPolicyQuery.findValidPolicy(TENANT_CODE, CERTIFICATE_NO))
                .thenReturn(Optional.of(new PolicySnapshot("P20261028001")));
        when(transactionService.promoteToWaitAudit(any(), anyString())).thenReturn(true);

        underwritingUseCase.dispatch(APPLICATION_NO, TRACE_ID);

        verify(transactionService).promoteToWaitAudit(any(), anyString());
        verify(transactionService, never()).startUnderwriting(any());
        verify(autoUnderwritingRequester, never()).request(anyString(), anyString(), anyString());
        assertThat(capturedActions()).containsExactly(InteractionAction.STARTED, InteractionAction.COMPLETED);
    }

    /**
     * 不存在有效保单时推进自动承保，并在状态提交后发起外部请求。
     */
    @Test
    void shouldStartAutoUnderwritingWhenNoValidPolicy() {
        when(validPolicyQuery.findValidPolicy(TENANT_CODE, CERTIFICATE_NO)).thenReturn(Optional.empty());
        when(transactionService.startUnderwriting(any())).thenReturn(true);

        underwritingUseCase.dispatch(APPLICATION_NO, TRACE_ID);

        verify(transactionService).startUnderwriting(any());
        verify(transactionService, never()).promoteToWaitAudit(any(), anyString());
        verify(autoUnderwritingRequester).request(APPLICATION_NO, TENANT_CODE, CERTIFICATE_NO);
        assertThat(capturedActions()).containsExactly(InteractionAction.STARTED, InteractionAction.COMPLETED,
                InteractionAction.STARTED, InteractionAction.COMPLETED);
    }

    /**
     * 保单查询失败时保持已受理状态，只记录失败轨迹，由补偿任务重试。
     */
    @Test
    void shouldKeepAcceptedWhenPolicyQueryFails() {
        when(validPolicyQuery.findValidPolicy(TENANT_CODE, CERTIFICATE_NO))
                .thenThrow(new IllegalStateException("承保系统超时"));

        underwritingUseCase.dispatch(APPLICATION_NO, TRACE_ID);

        verify(transactionService, never()).promoteToWaitAudit(any(), anyString());
        verify(transactionService, never()).startUnderwriting(any());
        verify(autoUnderwritingRequester, never()).request(anyString(), anyString(), anyString());
        assertThat(capturedActions()).containsExactly(InteractionAction.STARTED, InteractionAction.FAILED);
    }

    /**
     * 自动承保请求失败时保持承保中状态，只记录失败轨迹。
     */
    @Test
    void shouldRecordFailureWhenAutoUnderwritingRequestFails() {
        when(validPolicyQuery.findValidPolicy(TENANT_CODE, CERTIFICATE_NO)).thenReturn(Optional.empty());
        when(transactionService.startUnderwriting(any())).thenReturn(true);
        doThrow(new IllegalStateException("承保系统不可用"))
                .when(autoUnderwritingRequester).request(anyString(), anyString(), anyString());

        underwritingUseCase.dispatch(APPLICATION_NO, TRACE_ID);

        assertThat(capturedActions()).containsExactly(InteractionAction.STARTED, InteractionAction.COMPLETED,
                InteractionAction.STARTED, InteractionAction.FAILED);
        InteractionLog failedLog = capturedLogs().get(3);
        assertThat(failedLog.errorCode()).isEqualTo("AUTO_UNDERWRITING_REQUEST_FAILED");
        assertThat(failedLog.resultStatus()).isEqualTo("FAILED");
    }

    /**
     * 状态条件不满足时不读取人员信息，也不发起任何外部调用。
     */
    @Test
    void shouldSkipWhenApplicationNotAccepted() {
        Application waitAuditApplication = acceptedApplication();
        waitAuditApplication.setStatus(ApplicationStatus.WAIT_AUDIT);
        when(applicationRepository.findByApplicationNo(APPLICATION_NO))
                .thenReturn(Optional.of(waitAuditApplication));

        underwritingUseCase.dispatch(APPLICATION_NO, TRACE_ID);

        verifyNoInteractions(applicationPersonReader, validPolicyQuery, autoUnderwritingRequester, transactionService,
                interactionLogRepository);
    }

    /**
     * 状态推进失败说明其他执行者已处理，不再发起自动承保请求。
     */
    @Test
    void shouldNotRequestAutoUnderwritingWhenStartUnderwritingLosesRace() {
        when(validPolicyQuery.findValidPolicy(TENANT_CODE, CERTIFICATE_NO)).thenReturn(Optional.empty());
        when(transactionService.startUnderwriting(any())).thenReturn(false);

        underwritingUseCase.dispatch(APPLICATION_NO, TRACE_ID);

        verify(autoUnderwritingRequester, never()).request(anyString(), anyString(), anyString());
        verify(interactionLogRepository, times(2)).insert(any());
    }

    /**
     * 申请人不存在属于数据一致性问题，直接抛出。
     */
    @Test
    void shouldFailWhenApplicationMissing() {
        when(applicationRepository.findByApplicationNo(APPLICATION_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> underwritingUseCase.dispatch(APPLICATION_NO, TRACE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(APPLICATION_NO);
    }

    private List<InteractionAction> capturedActions() {
        return capturedLogs().stream()
                .map(interactionLog -> InteractionAction.valueOf(interactionLog.action()))
                .toList();
    }

    private List<InteractionLog> capturedLogs() {
        ArgumentCaptor<InteractionLog> logCaptor = ArgumentCaptor.forClass(InteractionLog.class);
        verify(interactionLogRepository, atLeastOnce()).insert(logCaptor.capture());
        return logCaptor.getAllValues();
    }

    private Application acceptedApplication() {
        Application application = Application.createPending(TENANT_CODE, APPLICATION_NO, "EXT-001", "{}", "{}", "{}",
                "{}");
        application.setStatus(ApplicationStatus.ACCEPTED);
        return application;
    }
}
