package com.wangguangwu.claim.application.usecase.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.domain.claim.ApplicationExistenceQuery;
import com.wangguangwu.claim.application.domain.claim.ApplicationIntakeLock;
import com.wangguangwu.claim.application.domain.claim.ApplicationNoGenerator;
import com.wangguangwu.claim.application.domain.claim.Gender;
import com.wangguangwu.claim.application.domain.claim.PersonInfo;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.usecase.intake.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.usecase.intake.result.ApplicationIntakeDisposition;
import com.wangguangwu.claim.application.usecase.intake.result.ApplicationIntakeResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 申请签收用例入口测试。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class ApplicationIntakeUseCaseTest {

    private static final String CANDIDATE_APPLICATION_NO = "A2026102800000001";
    private static final String EXISTING_APPLICATION_NO = "A2026102700000009";

    private final ApplicationIntakeLock applicationIntakeLock = mock(ApplicationIntakeLock.class);
    private final ApplicationExistenceQuery applicationExistenceQuery = mock(ApplicationExistenceQuery.class);
    private final ApplicationNoGenerator applicationNoGenerator = mock(ApplicationNoGenerator.class);
    private final ApplicationIntakeTransactionService transactionService = mock(
            ApplicationIntakeTransactionService.class);

    private ApplicationIntakeUseCase intakeUseCase;

    /**
     * 初始化待测服务。
     */
    @BeforeEach
    void setUp() {
        intakeUseCase = new ApplicationIntakeUseCase(applicationIntakeLock, applicationExistenceQuery,
                applicationNoGenerator, transactionService);
    }

    /**
     * 首次材料为空时必须在获取分布式锁之前拒绝请求。
     */
    @Test
    void shouldRejectApplicationWithoutMaterialsBeforeLock() {
        ApplicationIntakeCommand command = command(List.of());

        assertThatThrownBy(() -> intakeUseCase.intake(command, "request-1", "trace-1"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("首次签收至少需要一项材料");
        verifyNoInteractions(applicationIntakeLock, applicationExistenceQuery, applicationNoGenerator,
                transactionService);
    }

    /**
     * 人员性别缺失属于标准字段校验失败，必须在事务外拒绝。
     */
    @Test
    void shouldRejectApplicationWithoutPersonGender() {
        PersonInfo personWithoutGender = new PersonInfo("张三", null, "ID_CARD", "110101199001011234");
        ApplicationIntakeCommand command = new ApplicationIntakeCommand("tenant-a", "external-1",
                personWithoutGender, personWithoutGender, personWithoutGender, personWithoutGender,
                List.of(new MaterialSubmission("IMAGE", "https://example.com/image.jpg")));

        assertThatThrownBy(() -> intakeUseCase.intake(command, "request-1", "trace-1"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("申请人的姓名、性别、证件类型和证件号不能为空");
        verifyNoInteractions(applicationIntakeLock, transactionService);
    }

    /**
     * 存在性判断为否时，必须在事务外生成编号并创建申请。
     */
    @Test
    void shouldPrepareApplicationNoOutsideTransactionForFirstIntake() {
        givenLockExecutesAction();
        givenApplicationNo();
        when(applicationExistenceQuery.exists("tenant-a", "external-1")).thenReturn(false);
        when(transactionService.intake(any(), eq(false), eq(CANDIDATE_APPLICATION_NO), anyString(), anyString()))
                .thenReturn(new ApplicationIntakeResult(CANDIDATE_APPLICATION_NO,
                        ApplicationIntakeDisposition.CREATED));

        ApplicationIntakeResult result = intakeUseCase.intake(command(), "request-1", "trace-1");

        assertThat(result.applicationNo()).isEqualTo(CANDIDATE_APPLICATION_NO);
        verify(applicationNoGenerator).nextApplicationNo();
        verify(applicationExistenceQuery).evict("tenant-a", "external-1");
    }

    /**
     * 已存在申请时不得消耗编号，重复请求也不刷新存在性缓存。
     */
    @Test
    void shouldReuseExistingApplicationWithoutGeneratingNewNo() {
        givenLockExecutesAction();
        when(applicationExistenceQuery.exists("tenant-a", "external-1")).thenReturn(true);
        when(transactionService.intake(any(), eq(true), isNull(), anyString(), anyString()))
                .thenReturn(new ApplicationIntakeResult(EXISTING_APPLICATION_NO,
                        ApplicationIntakeDisposition.REUSED));

        ApplicationIntakeResult result = intakeUseCase.intake(command(), "request-1", "trace-1");

        assertThat(result.applicationNo()).isEqualTo(EXISTING_APPLICATION_NO);
        verify(applicationNoGenerator, never()).nextApplicationNo();
        verify(applicationExistenceQuery, never()).evict(anyString(), anyString());
    }

    /**
     * 存在性缓存与数据库不一致时，必须在事务外重新准备编号并再次签收。
     */
    @Test
    void shouldRetryWithNewApplicationNoWhenExistenceCacheIsStale() {
        givenLockExecutesAction();
        givenApplicationNo();
        when(applicationExistenceQuery.exists("tenant-a", "external-1")).thenReturn(true);
        when(transactionService.intake(any(), eq(true), isNull(), anyString(), anyString()))
                .thenThrow(new ApplicationExistenceMismatchException());
        when(transactionService.intake(any(), eq(false), eq(CANDIDATE_APPLICATION_NO), anyString(), anyString()))
                .thenReturn(new ApplicationIntakeResult(CANDIDATE_APPLICATION_NO,
                        ApplicationIntakeDisposition.CREATED));

        ApplicationIntakeResult result = intakeUseCase.intake(command(), "request-1", "trace-1");

        assertThat(result.applicationNo()).isEqualTo(CANDIDATE_APPLICATION_NO);
        verify(applicationExistenceQuery, times(2)).evict("tenant-a", "external-1");
        verify(transactionService, times(2)).intake(any(), anyBoolean(), any(), anyString(), anyString());
    }

    /**
     * 唯一键冲突且并发请求已落库时，应直接返回并发结果，不重复创建。
     */
    @Test
    void shouldReturnResultOfConcurrentRequestOnDuplicateKey() {
        givenLockExecutesAction();
        givenApplicationNo();
        when(applicationExistenceQuery.exists("tenant-a", "external-1")).thenReturn(false);
        when(transactionService.intake(any(), eq(false), eq(CANDIDATE_APPLICATION_NO), anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("uk_application_no"));
        when(transactionService.resolveAfterDuplicateKey(any(), anyString(), anyString()))
                .thenReturn(Optional.of(new ApplicationIntakeResult(EXISTING_APPLICATION_NO,
                        ApplicationIntakeDisposition.REUSED)));

        ApplicationIntakeResult result = intakeUseCase.intake(command(), "request-1", "trace-1");

        assertThat(result.applicationNo()).isEqualTo(EXISTING_APPLICATION_NO);
        verify(applicationExistenceQuery, never()).evict(anyString(), anyString());
        verify(applicationNoGenerator, never()).synchronizeDailyCounter();
    }

    /**
     * 唯一键冲突但并发请求尚未提交时，应淘汰缓存后重新签收。
     */
    @Test
    void shouldRetryAfterDuplicateKeyWithoutExistingApplication() {
        givenLockExecutesAction();
        givenApplicationNo();
        when(applicationExistenceQuery.exists("tenant-a", "external-1")).thenReturn(false, true);
        when(transactionService.intake(any(), eq(false), eq(CANDIDATE_APPLICATION_NO), anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("uk_application_no"));
        when(transactionService.resolveAfterDuplicateKey(any(), anyString(), anyString())).thenReturn(Optional.empty());
        when(transactionService.intake(any(), eq(true), isNull(), anyString(), anyString()))
                .thenReturn(new ApplicationIntakeResult(EXISTING_APPLICATION_NO,
                        ApplicationIntakeDisposition.REUSED));

        ApplicationIntakeResult result = intakeUseCase.intake(command(), "request-1", "trace-1");

        assertThat(result.applicationNo()).isEqualTo(EXISTING_APPLICATION_NO);
        verify(applicationExistenceQuery).evict("tenant-a", "external-1");
        verify(applicationNoGenerator).synchronizeDailyCounter();
    }

    /**
     * 让分布式锁直接执行受保护的签收逻辑，聚焦用例层编排。
     */
    private void givenLockExecutesAction() {
        when(applicationIntakeLock.execute(anyString(), anyString(), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(2)).get());
    }

    /**
     * 固定首次签收的候选编号。
     */
    private void givenApplicationNo() {
        when(applicationNoGenerator.nextApplicationNo()).thenReturn(CANDIDATE_APPLICATION_NO);
    }

    /**
     * 构建满足统一接入字段要求的受理命令。
     *
     * @return 测试受理命令
     */
    private ApplicationIntakeCommand command() {
        return command(List.of(new MaterialSubmission("IMAGE", "https://example.com/image.jpg")));
    }

    /**
     * 构建指定材料的受理命令。
     *
     * @param materials 本次提交的材料
     * @return 测试受理命令
     */
    private ApplicationIntakeCommand command(List<MaterialSubmission> materials) {
        PersonInfo person = new PersonInfo("张三", Gender.MALE, "ID_CARD", "110101199001011234");
        return new ApplicationIntakeCommand("tenant-a", "external-1", person, person, person, person, materials);
    }
}
