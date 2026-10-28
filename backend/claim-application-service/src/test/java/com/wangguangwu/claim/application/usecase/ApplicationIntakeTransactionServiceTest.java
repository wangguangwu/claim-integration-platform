package com.wangguangwu.claim.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangguangwu.claim.application.domain.application.Application;
import com.wangguangwu.claim.application.domain.application.ApplicationRepository;
import com.wangguangwu.claim.application.domain.application.ApplicationStatus;
import com.wangguangwu.claim.application.domain.interaction.InteractionLog;
import com.wangguangwu.claim.application.domain.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.material.MaterialBatchNoGenerator;
import com.wangguangwu.claim.application.domain.material.MaterialRepository;
import com.wangguangwu.claim.application.domain.outbox.OutboxEventRepository;
import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.command.Gender;
import com.wangguangwu.claim.application.usecase.command.MaterialCommand;
import com.wangguangwu.claim.application.usecase.command.PersonInfo;
import com.wangguangwu.claim.application.usecase.result.ApplicationIntakeDisposition;
import com.wangguangwu.claim.application.usecase.result.ApplicationIntakeResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 申请签收本地事务编排测试。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class ApplicationIntakeTransactionServiceTest {

    private static final String CANDIDATE_APPLICATION_NO = "A2026102800000001";
    private static final String EXISTING_APPLICATION_NO = "A2026102700000009";

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final MaterialRepository materialRepository = mock(MaterialRepository.class);
    private final InteractionLogRepository interactionLogRepository = mock(InteractionLogRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final MaterialBatchNoGenerator materialBatchNoGenerator = mock(MaterialBatchNoGenerator.class);

    private ApplicationIntakeTransactionService transactionService;

    /**
     * 初始化待测服务与固定批次编号结果。
     */
    @BeforeEach
    void setUp() {
        transactionService = new ApplicationIntakeTransactionService(applicationRepository, materialRepository,
                interactionLogRepository, outboxEventRepository,
                new ApplicationIntakeRecordFactory(new ObjectMapper(), materialBatchNoGenerator));
        when(materialBatchNoGenerator.nextBatchNo()).thenReturn("B20261028batch");
    }

    /**
     * 首次请求应使用候选编号创建 Application、同批次材料、Outbox 和开始完成日志。
     */
    @Test
    void shouldCreateApplicationForFirstIntake() {
        ApplicationIntakeResult result = transactionService.intake(command(), false, CANDIDATE_APPLICATION_NO,
                "request-1", "trace-1");

        assertThat(result.applicationNo()).isEqualTo(CANDIDATE_APPLICATION_NO);
        assertThat(result.disposition()).isEqualTo(ApplicationIntakeDisposition.CREATED);
        ArgumentCaptor<Application> applicationCaptor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).insert(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().getApplicationNo()).isEqualTo(CANDIDATE_APPLICATION_NO);
        assertThat(applicationCaptor.getValue().getStatus()).isEqualTo(ApplicationStatus.PENDING_ACCEPT);
        verify(materialRepository).insertAll(anyList());
        verify(outboxEventRepository).insert(any());
        verify(interactionLogRepository, times(2)).insert(any());
    }

    /**
     * 非受理失败状态的重复请求只能复用编号，不得新增材料和 Outbox。
     */
    @Test
    void shouldReuseApplicationForNonFailedDuplicate() {
        when(applicationRepository.findByTenantAndExternalApplicationNo("tenant-a", "external-1"))
                .thenReturn(Optional.of(application(ApplicationStatus.PENDING_ACCEPT)));

        ApplicationIntakeResult result = transactionService.intake(command(), true, null, "request-1", "trace-1");

        assertThat(result.applicationNo()).isEqualTo(EXISTING_APPLICATION_NO);
        assertThat(result.disposition()).isEqualTo(ApplicationIntakeDisposition.REUSED);
        verify(applicationRepository, never()).insert(any());
        verify(materialRepository, never()).insertAll(anyList());
        verify(outboxEventRepository, never()).insert(any());
        verify(interactionLogRepository, times(2)).insert(any());
    }

    /**
     * 受理失败申请重新提交时，应重置状态、软删除旧材料并产生新的 Outbox。
     */
    @Test
    void shouldReapplyFailedApplication() {
        Application application = application(ApplicationStatus.ACCEPT_FAILED);
        when(applicationRepository.findByTenantAndExternalApplicationNo("tenant-a", "external-1"))
                .thenReturn(Optional.of(application));
        when(applicationRepository.updateForReapply(application)).thenReturn(true);

        ApplicationIntakeResult result = transactionService.intake(command(), true, null, "request-1", "trace-1");

        assertThat(result.applicationNo()).isEqualTo(EXISTING_APPLICATION_NO);
        assertThat(result.disposition()).isEqualTo(ApplicationIntakeDisposition.REAPPLIED);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING_ACCEPT);
        assertThat(application.getApplicationAttempt()).isEqualTo(2);
        verify(materialRepository).softDeleteByApplicationNo(EXISTING_APPLICATION_NO);
        verify(materialRepository).insertAll(anyList());
        verify(outboxEventRepository).insert(any());
        verify(interactionLogRepository, times(2)).insert(any());
    }

    /**
     * 并发重新申请时状态条件更新失败的一方必须复用申请，不得覆盖对方写入。
     */
    @Test
    void shouldReuseApplicationWhenConcurrentReapplyWins() {
        Application application = application(ApplicationStatus.ACCEPT_FAILED);
        when(applicationRepository.findByTenantAndExternalApplicationNo("tenant-a", "external-1"))
                .thenReturn(Optional.of(application));
        when(applicationRepository.updateForReapply(application)).thenReturn(false);

        ApplicationIntakeResult result = transactionService.intake(command(), true, null, "request-1", "trace-1");

        assertThat(result.applicationNo()).isEqualTo(EXISTING_APPLICATION_NO);
        assertThat(result.disposition()).isEqualTo(ApplicationIntakeDisposition.REUSED);
        verify(materialRepository, never()).softDeleteByApplicationNo(any());
        verify(materialRepository, never()).insertAll(anyList());
        verify(outboxEventRepository, never()).insert(any());
        verify(interactionLogRepository, times(2)).insert(any());
    }

    /**
     * 存在性缓存命中但数据库无对应记录时，必须交由用例层重新准备编号。
     */
    @Test
    void shouldRequireRetryWhenExistenceCacheIsStale() {
        when(applicationRepository.findByTenantAndExternalApplicationNo("tenant-a", "external-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.intake(command(), true, null, "request-1", "trace-1"))
                .isInstanceOf(ApplicationIntakeCacheStaleException.class);
        verify(applicationRepository, never()).insert(any());
        verify(interactionLogRepository, never()).insert(any());
    }

    /**
     * 唯一键冲突后应按已存在申请复用编号，并记录可追踪的交互事件。
     */
    @Test
    void shouldResolveDuplicateKeyByReadingExistingApplication() {
        when(applicationRepository.findByTenantAndExternalApplicationNo("tenant-a", "external-1"))
                .thenReturn(Optional.of(application(ApplicationStatus.PENDING_ACCEPT)));

        Optional<ApplicationIntakeResult> result = transactionService.resolveAfterDuplicateKey(command(), "request-1",
                "trace-1");

        assertThat(result).isPresent();
        assertThat(result.get().disposition()).isEqualTo(ApplicationIntakeDisposition.REUSED);
        ArgumentCaptor<InteractionLog> captor = ArgumentCaptor.forClass(InteractionLog.class);
        verify(interactionLogRepository, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(interactionLog -> {
            assertThat(interactionLog.applicationNo()).isEqualTo(EXISTING_APPLICATION_NO);
            assertThat(interactionLog.requestId()).isEqualTo("request-1");
            assertThat(interactionLog.traceId()).isEqualTo("trace-1");
        });
    }

    /**
     * 构建满足统一接入字段要求的标准命令。
     *
     * @return 测试命令
     */
    private ApplicationIntakeCommand command() {
        PersonInfo person = new PersonInfo("张三", Gender.MALE, "ID_CARD", "110101199001011234");
        return new ApplicationIntakeCommand("tenant-a", "external-1", person, person, person, person,
                List.of(new MaterialCommand("IMAGE", "https://example.com/image.jpg")));
    }

    /**
     * 构建指定状态的现有 Application。
     *
     * @param status Application 状态
     * @return Application 聚合
     */
    private Application application(ApplicationStatus status) {
        Application application = Application.createPending("tenant-a", EXISTING_APPLICATION_NO, "external-1", "{}",
                "{}", "{}", "{}");
        application.setId(1L);
        application.setStatus(status);
        return application;
    }
}
