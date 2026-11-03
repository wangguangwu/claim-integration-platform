package com.wangguangwu.claim.application.application.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.application.port.InteractionLogRepository;
import com.wangguangwu.claim.application.application.port.OutboxEventRepository;
import com.wangguangwu.claim.application.application.command.UnderwritingCompletedCommand;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 承保完成通知事务服务单元测试：验证重复通知、版本过期与审核交付事件写入。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@DisplayName("承保完成通知事务服务")
class UnderwritingCallbackTransactionServiceTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261103-001";
    private static final String CLAIM_APPLICATION_NO = "A2026110300000001";
    private static final String REQUEST_ID = "req-001";
    private static final String TRACE_ID = "trace-001";
    private static final String INTERACTION_NO = "INT-001";

    private ClaimApplicationRepository claimApplicationRepository;
    private OutboxEventRepository outboxEventRepository;

    private UnderwritingCallbackTransactionService transactionService;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        InteractionLogRepository interactionLogRepository = mock(InteractionLogRepository.class);
        ClaimApplicationLifecycleRecordFactory recordFactory = new ClaimApplicationLifecycleRecordFactory(
                new ObjectMapper());

        transactionService = new UnderwritingCallbackTransactionService(claimApplicationRepository,
                outboxEventRepository, interactionLogRepository, recordFactory);
    }

    @Test
    @DisplayName("自动承保中：保存成功后写审核交付事件并返回完成")
    void complete_saved_writesOutbox() {
        ClaimApplication application = application(ClaimApplicationStatus.UNDERWRITING);
        when(claimApplicationRepository.save(application)).thenReturn(true);

        UnderwritingCallbackResult result = transactionService.complete(command(), application, INTERACTION_NO);

        assertThat(result.disposition()).isEqualTo(UnderwritingCallbackDisposition.COMPLETED);
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.WAIT_AUDIT);
        verify(outboxEventRepository).insert(any());
    }

    @Test
    @DisplayName("自动承保中：版本过期说明并发通知已推进，不写审核交付事件")
    void complete_versionConflict_returnsReusedWithoutOutbox() {
        ClaimApplication application = application(ClaimApplicationStatus.UNDERWRITING);
        when(claimApplicationRepository.save(application)).thenReturn(false);

        UnderwritingCallbackResult result = transactionService.complete(command(), application, INTERACTION_NO);

        assertThat(result.disposition()).isEqualTo(UnderwritingCallbackDisposition.REUSED);
        verify(outboxEventRepository, never()).insert(any());
    }

    @Test
    @DisplayName("申请不在自动承保中：按重复通知处理且不保存聚合")
    void complete_notUnderwriting_returnsReused() {
        ClaimApplication application = application(ClaimApplicationStatus.WAIT_AUDIT);

        UnderwritingCallbackResult result = transactionService.complete(command(), application, INTERACTION_NO);

        assertThat(result.disposition()).isEqualTo(UnderwritingCallbackDisposition.REUSED);
        verify(claimApplicationRepository, never()).save(any(ClaimApplication.class));
        verify(outboxEventRepository, never()).insert(any());
    }

    @Test
    @DisplayName("审核交付事件写入失败：异常向上抛出以回滚状态推进")
    void complete_outboxWriteFails_propagatesException() {
        ClaimApplication application = application(ClaimApplicationStatus.UNDERWRITING);
        when(claimApplicationRepository.save(application)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("Outbox 写入失败"))
                .when(outboxEventRepository).insert(any());

        assertThatThrownBy(() -> transactionService.complete(command(), application, INTERACTION_NO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Outbox 写入失败");
    }

    private UnderwritingCompletedCommand command() {
        return new UnderwritingCompletedCommand(CLAIM_APPLICATION_NO, REQUEST_ID, TRACE_ID);
    }

    private ClaimApplication application(ClaimApplicationStatus status) {
        return ClaimApplication.restore(1L, TENANT_CODE, CLAIM_APPLICATION_NO, EXTERNAL_NO, status, 1, null,
                new Applicant("张三", Gender.MALE, "身份证", "110101199001011234"),
                new PrimaryInsured("李四", Gender.MALE, "身份证", "110101199002022345"),
                null,
                new Payee("赵六", Gender.FEMALE, "身份证", "110101199004044567"),
                0L, List.of(), List.of());
    }
}
