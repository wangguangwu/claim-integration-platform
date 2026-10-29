package com.wangguangwu.claim.application.usecase.underwriting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.Gender;
import com.wangguangwu.claim.application.domain.claim.PersonInfo;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.shared.contract.ApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.shared.contract.ApplicationAsyncException;
import com.wangguangwu.claim.application.shared.contract.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.underwriting.result.UnderwritingCallbackDisposition;
import com.wangguangwu.claim.application.usecase.underwriting.result.UnderwritingCallbackResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 承保完成通知用例测试：参数校验、申请不存在与幂等委托。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class UnderwritingCallbackUseCaseTest {

    private static final String APPLICATION_NO = "A2026102800000001";
    private static final String TENANT_CODE = "tenant-a";
    private static final String REQUEST_ID = "callback-request-1";
    private static final String TRACE_ID = "trace-1";

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final InteractionLogRepository interactionLogRepository = mock(InteractionLogRepository.class);
    private final UnderwritingCallbackTransactionService transactionService =
            mock(UnderwritingCallbackTransactionService.class);
    private final ApplicationLifecycleRecordFactory recordFactory =
            new ApplicationLifecycleRecordFactory(new ObjectMapper());

    private UnderwritingCallbackUseCase underwritingCallbackUseCase;

    /**
     * 初始化待测用例。
     */
    @BeforeEach
    void setUp() {
        underwritingCallbackUseCase = new UnderwritingCallbackUseCase(applicationRepository,
                interactionLogRepository, recordFactory, transactionService);
    }

    /**
     * 缺少申请编号的回调返回参数非法业务错误。
     */
    @Test
    void shouldRejectCallbackWithoutApplicationNo() {
        UnderwritingCompletedCommand command = new UnderwritingCompletedCommand(" ", REQUEST_ID, TRACE_ID);

        assertThatThrownBy(() -> underwritingCallbackUseCase.complete(command))
                .isInstanceOf(ApplicationAsyncException.class)
                .extracting(exception -> ((ApplicationAsyncException) exception).getErrorCode())
                .isEqualTo(ApplicationAsyncErrorCode.CALLBACK_INVALID);
        verifyNoInteractions(applicationRepository, interactionLogRepository, transactionService);
    }

    /**
     * 缺少请求标识的回调返回参数非法业务错误。
     */
    @Test
    void shouldRejectCallbackWithoutRequestId() {
        UnderwritingCompletedCommand command = new UnderwritingCompletedCommand(APPLICATION_NO, null, TRACE_ID);

        assertThatThrownBy(() -> underwritingCallbackUseCase.complete(command))
                .isInstanceOf(ApplicationAsyncException.class)
                .extracting(exception -> ((ApplicationAsyncException) exception).getErrorCode())
                .isEqualTo(ApplicationAsyncErrorCode.CALLBACK_INVALID);
        verifyNoInteractions(applicationRepository, interactionLogRepository, transactionService);
    }

    /**
     * 申请不存在时返回明确业务错误，不创建任何数据。
     */
    @Test
    void shouldRejectCallbackForMissingApplication() {
        when(applicationRepository.findByApplicationNo(APPLICATION_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> underwritingCallbackUseCase.complete(command()))
                .isInstanceOf(ApplicationAsyncException.class)
                .extracting(exception -> ((ApplicationAsyncException) exception).getErrorCode())
                .isEqualTo(ApplicationAsyncErrorCode.APPLICATION_NOT_FOUND);
        verifyNoInteractions(interactionLogRepository, transactionService);
    }

    /**
     * 有效通知先写开始轨迹再交给事务服务推进状态。
     */
    @Test
    void shouldDelegateValidCallbackToTransactionService() {
        when(applicationRepository.findByApplicationNo(APPLICATION_NO))
                .thenReturn(Optional.of(underwritingApplication()));
        when(transactionService.complete(any(), any(), anyString()))
                .thenReturn(new UnderwritingCallbackResult(APPLICATION_NO, UnderwritingCallbackDisposition.COMPLETED));

        UnderwritingCallbackResult result = underwritingCallbackUseCase.complete(command());

        assertThat(result.disposition()).isEqualTo(UnderwritingCallbackDisposition.COMPLETED);
        verify(interactionLogRepository).insert(any());
        verify(transactionService).complete(any(), any(), anyString());
    }

    /**
     * 状态不匹配的通知仍返回成功，事务服务给出复用结果。
     */
    @Test
    void shouldReturnReusedResultForDuplicateCallback() {
        when(applicationRepository.findByApplicationNo(APPLICATION_NO))
                .thenReturn(Optional.of(underwritingApplication()));
        when(transactionService.complete(any(), any(), anyString()))
                .thenReturn(new UnderwritingCallbackResult(APPLICATION_NO, UnderwritingCallbackDisposition.REUSED));

        UnderwritingCallbackResult result = underwritingCallbackUseCase.complete(command());

        assertThat(result.disposition()).isEqualTo(UnderwritingCallbackDisposition.REUSED);
    }

    private UnderwritingCompletedCommand command() {
        return new UnderwritingCompletedCommand(APPLICATION_NO, REQUEST_ID, TRACE_ID);
    }

    private ClaimApplication underwritingApplication() {
        PersonInfo person = new PersonInfo("张三", Gender.MALE, "ID_CARD", "110101199001010011");
        return ClaimApplication.restore(1L, TENANT_CODE, APPLICATION_NO, "EXT-001",
                ApplicationStatus.UNDERWRITING, 1, person, person, person, person);
    }
}
