package com.wangguangwu.claim.application.usecase.underwriting;

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
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationErrorCode;
import com.wangguangwu.claim.application.domain.claim.person.Applicant;
import com.wangguangwu.claim.application.domain.claim.person.Gender;
import com.wangguangwu.claim.application.domain.claim.person.Payee;
import com.wangguangwu.claim.application.domain.claim.person.PrimaryInsured;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.exception.DomainException;
import com.wangguangwu.claim.application.usecase.integration.interaction.port.InteractionLogRepository;
import com.wangguangwu.claim.application.usecase.integration.outbox.port.OutboxEventRepository;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 承保分流事务服务单元测试：验证状态推进、保存冲突与审核交付事件的写入顺序。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@DisplayName("承保分流事务服务")
class UnderwritingTransactionServiceTest {

    private static final String TENANT_CODE = "tenant-a";
    private static final String EXTERNAL_NO = "EXT-20261103-001";
    private static final String CLAIM_APPLICATION_NO = "A2026110300000001";
    private static final String TRACE_ID = "trace-001";

    private ClaimApplicationRepository claimApplicationRepository;
    private OutboxEventRepository outboxEventRepository;

    private UnderwritingTransactionService transactionService;

    @BeforeEach
    void setUp() {
        claimApplicationRepository = mock(ClaimApplicationRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        InteractionLogRepository interactionLogRepository = mock(InteractionLogRepository.class);
        ClaimApplicationLifecycleRecordFactory recordFactory = new ClaimApplicationLifecycleRecordFactory(
                new ObjectMapper());

        transactionService = new UnderwritingTransactionService(claimApplicationRepository, outboxEventRepository,
                interactionLogRepository, recordFactory);
    }

    @Test
    @DisplayName("有有效保单：保存成功后写审核交付事件")
    void promoteToWaitAudit_saved_writesOutbox() {
        ClaimApplication application = application(ClaimApplicationStatus.ACCEPTED);
        when(claimApplicationRepository.save(application)).thenReturn(true);

        boolean promoted = transactionService.promoteToWaitAudit(application, TRACE_ID);

        assertThat(promoted).isTrue();
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.WAIT_AUDIT);
        verify(outboxEventRepository).insert(any());
    }

    @Test
    @DisplayName("有有效保单：版本冲突时不写审核交付事件")
    void promoteToWaitAudit_versionConflict_skipsOutbox() {
        ClaimApplication application = application(ClaimApplicationStatus.ACCEPTED);
        when(claimApplicationRepository.save(application)).thenReturn(false);

        boolean promoted = transactionService.promoteToWaitAudit(application, TRACE_ID);

        assertThat(promoted).isFalse();
        verify(outboxEventRepository, never()).insert(any());
    }

    @Test
    @DisplayName("无有效保单：保存失败按重复事件处理")
    void startUnderwriting_versionConflict_returnsFalse() {
        ClaimApplication application = application(ClaimApplicationStatus.ACCEPTED);
        when(claimApplicationRepository.save(application)).thenReturn(false);

        boolean started = transactionService.startUnderwriting(application);

        assertThat(started).isFalse();
        assertThat(application.getStatus()).isEqualTo(ClaimApplicationStatus.UNDERWRITING);
    }

    @Test
    @DisplayName("审核交付事件写入失败：异常向上抛出以回滚状态推进")
    void promoteToWaitAudit_outboxWriteFails_propagatesException() {
        ClaimApplication application = application(ClaimApplicationStatus.ACCEPTED);
        when(claimApplicationRepository.save(application)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("Outbox 写入失败"))
                .when(outboxEventRepository).insert(any());

        assertThatThrownBy(() -> transactionService.promoteToWaitAudit(application, TRACE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Outbox 写入失败");
    }

    @Test
    @DisplayName("非已受理状态推进：领域校验异常向上抛出")
    void promoteToWaitAudit_wrongStatus_propagatesException() {
        ClaimApplication application = application(ClaimApplicationStatus.WAIT_AUDIT);

        assertThatThrownBy(() -> transactionService.promoteToWaitAudit(application, TRACE_ID))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("期望状态=ACCEPTED")
                .extracting("errorCode")
                .isEqualTo(ClaimApplicationErrorCode.CLAIM_APPLICATION_STATUS_INVALID);
        verify(outboxEventRepository, never()).insert(any());
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
