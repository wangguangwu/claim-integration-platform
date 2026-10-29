package com.wangguangwu.claim.application.support.mock.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResult;
import com.wangguangwu.claim.application.shared.contract.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.usecase.underwriting.UnderwritingCallbackUseCase;
import com.wangguangwu.claim.application.usecase.underwriting.result.UnderwritingCallbackDisposition;
import com.wangguangwu.claim.application.usecase.underwriting.result.UnderwritingCallbackResult;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 模拟承保测试：保单登记查询、自动承保幂等、完成通知与延迟完成。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class MockUnderwritingStoreTest {

    private static final String APPLICATION_NO = "A2026102800000001";
    private static final String TENANT_CODE = "tenant-a";
    private static final String CERTIFICATE_NO = "110101199001010011";

    private final UnderwritingCallbackUseCase underwritingCallbackUseCase = mock(UnderwritingCallbackUseCase.class);
    private MockUnderwritingStore mockUnderwritingStore;

    /**
     * 关闭模拟完成调度线程，避免用例间互相影响。
     */
    @AfterEach
    void tearDown() {
        if (mockUnderwritingStore != null) {
            mockUnderwritingStore.shutdown();
        }
    }

    /**
     * 登记保单后查询返回保单号，清除后返回无有效保单。
     */
    @Test
    void shouldRegisterAndRemovePolicy() {
        mockUnderwritingStore = store(false, Duration.ofHours(1));
        mockUnderwritingStore.registerPolicy(CERTIFICATE_NO, "P20261028001");

        assertThat(mockUnderwritingStore.queryPolicy(CERTIFICATE_NO).hasValidPolicy()).isTrue();
        assertThat(mockUnderwritingStore.queryPolicy(CERTIFICATE_NO).policyNo()).isEqualTo("P20261028001");

        mockUnderwritingStore.removePolicy(CERTIFICATE_NO);

        assertThat(mockUnderwritingStore.queryPolicy(CERTIFICATE_NO).hasValidPolicy()).isFalse();
        assertThat(mockUnderwritingStore.policies()).isEmpty();
    }

    /**
     * 同一申请编号重复发起只保留一份承保记录，通知标识保持不变。
     */
    @Test
    void shouldKeepSingleRecordWhenAutoUnderwritingRequestedTwice() {
        mockUnderwritingStore = store(false, Duration.ofHours(1));

        MockUnderwritingStore.AutoUnderwritingRecord firstRecord =
                mockUnderwritingStore.requestAutoUnderwriting(command());
        MockUnderwritingStore.AutoUnderwritingRecord secondRecord =
                mockUnderwritingStore.requestAutoUnderwriting(command());

        assertThat(firstRecord.applicationNo()).isEqualTo(APPLICATION_NO);
        assertThat(firstRecord.status()).isEqualTo(UnderwritingResult.PROCESSING);
        assertThat(secondRecord.notificationRequestId()).isEqualTo(firstRecord.notificationRequestId());
        verifyNoInteractions(underwritingCallbackUseCase);
    }

    /**
     * 手工触发完成应立即完成并发送承保完成通知。
     */
    @Test
    void shouldNotifyCompletionWhenManuallyCompleted() {
        when(underwritingCallbackUseCase.complete(any())).thenReturn(
                new UnderwritingCallbackResult(APPLICATION_NO, UnderwritingCallbackDisposition.COMPLETED));
        mockUnderwritingStore = store(false, Duration.ofHours(1));
        MockUnderwritingStore.AutoUnderwritingRecord record = mockUnderwritingStore.requestAutoUnderwriting(command());

        mockUnderwritingStore.completeAutoUnderwriting(APPLICATION_NO);

        ArgumentCaptor<UnderwritingCompletedCommand> commandCaptor =
                ArgumentCaptor.forClass(UnderwritingCompletedCommand.class);
        verify(underwritingCallbackUseCase).complete(commandCaptor.capture());
        assertThat(commandCaptor.getValue().applicationNo()).isEqualTo(APPLICATION_NO);
        assertThat(commandCaptor.getValue().requestId()).isEqualTo(record.notificationRequestId());
        assertThat(mockUnderwritingStore.findAutoUnderwriting(APPLICATION_NO).orElseThrow().status())
                .isEqualTo(UnderwritingResult.COMPLETED);
    }

    /**
     * 开启完成通知后，延迟完成会自动触发承保完成通知。
     */
    @Test
    void shouldNotifyCompletionWhenCompletionDelayElapsed() {
        when(underwritingCallbackUseCase.complete(any())).thenReturn(
                new UnderwritingCallbackResult(APPLICATION_NO, UnderwritingCallbackDisposition.COMPLETED));
        mockUnderwritingStore = store(true, Duration.ofMillis(20));

        mockUnderwritingStore.requestAutoUnderwriting(command());

        verify(underwritingCallbackUseCase, timeout(2000)).complete(any());
        assertThat(mockUnderwritingStore.findAutoUnderwriting(APPLICATION_NO).orElseThrow().status())
                .isEqualTo(UnderwritingResult.COMPLETED);
    }

    /**
     * 未登记的申请查询承保结果为空，控制器据此返回 NOT_FOUND。
     */
    @Test
    void shouldReturnEmptyForUnknownAutoUnderwriting() {
        mockUnderwritingStore = store(false, Duration.ofHours(1));

        assertThat(mockUnderwritingStore.findAutoUnderwriting(APPLICATION_NO)).isEmpty();
    }

    private MockUnderwritingStore store(boolean notifyOnComplete, Duration completionDelay) {
        return new MockUnderwritingStore(new MockUnderwritingProperties(notifyOnComplete, completionDelay),
                underwritingCallbackUseCase);
    }

    private MockUnderwritingStore.AutoUnderwritingCommand command() {
        return new MockUnderwritingStore.AutoUnderwritingCommand(APPLICATION_NO, TENANT_CODE, CERTIFICATE_NO);
    }
}
