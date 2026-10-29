package com.wangguangwu.claim.application.support.mock.infrastructure;

import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResult;
import com.wangguangwu.claim.application.shared.contract.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.usecase.underwriting.UnderwritingCallbackUseCase;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 内存模拟承保系统，维护有效保单列表与自动承保记录。
 *
 * <p>模拟数据不落库，重启后清空；自动完成时按配置决定是否在进程内触发承保完成通知。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "claim.mock.enabled", havingValue = "true")
public class MockUnderwritingStore {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final Map<String, PolicyRecord> policiesByCertificateNo = new ConcurrentHashMap<>();
    private final Map<String, AutoUnderwritingRecord> autoUnderwritingsByApplicationNo = new ConcurrentHashMap<>();
    private final ScheduledExecutorService completionScheduler = Executors.newSingleThreadScheduledExecutor();
    private final MockUnderwritingProperties properties;
    private final UnderwritingCallbackUseCase underwritingCallbackUseCase;

    /**
     * 登记有效保单，重复登记覆盖旧保单号。
     *
     * @param certificateNo 被保人证件号
     * @param policyNo      保单号
     */
    public void registerPolicy(String certificateNo, String policyNo) {
        policiesByCertificateNo.put(certificateNo,
                new PolicyRecord(certificateNo, policyNo, LocalDateTime.now(BUSINESS_ZONE_ID)));
    }

    /**
     * 清除有效保单。
     *
     * @param certificateNo 被保人证件号
     * @return 存在并被移除时为 true
     */
    public boolean removePolicy(String certificateNo) {
        return policiesByCertificateNo.remove(certificateNo) != null;
    }

    /**
     * 查看当前保单，不返回人员姓名。
     *
     * @return 保单列表
     */
    public List<PolicyRecord> policies() {
        return List.copyOf(policiesByCertificateNo.values());
    }

    /**
     * 查询被保人是否已有有效保单。
     *
     * @param certificateNo 被保人证件号
     * @return 保单查询结果
     */
    public PolicyQueryOutcome queryPolicy(String certificateNo) {
        PolicyRecord policyRecord = policiesByCertificateNo.get(certificateNo);
        return policyRecord == null
                ? PolicyQueryOutcome.absent()
                : PolicyQueryOutcome.present(policyRecord.policyNo());
    }

    /**
     * 受理自动承保，按平台申请编号幂等，并按配置延迟完成。
     *
     * @param command 自动承保命令
     * @return 最新承保记录
     */
    public AutoUnderwritingRecord requestAutoUnderwriting(AutoUnderwritingCommand command) {
        return autoUnderwritingsByApplicationNo.computeIfAbsent(command.applicationNo(), applicationNo -> {
            AutoUnderwritingRecord record = new AutoUnderwritingRecord(applicationNo, UnderwritingResult.PROCESSING,
                    UUID.randomUUID().toString(), LocalDateTime.now(BUSINESS_ZONE_ID), null);
            scheduleCompletion(record);
            return record;
        });
    }

    /**
     * 查询自动承保结果。
     *
     * @param applicationNo 平台申请编号
     * @return 承保记录；不存在时为空
     */
    public Optional<AutoUnderwritingRecord> findAutoUnderwriting(String applicationNo) {
        return Optional.ofNullable(autoUnderwritingsByApplicationNo.get(applicationNo));
    }

    /**
     * 手工触发完成并始终发送承保完成通知，供联调使用。
     *
     * @param applicationNo 平台申请编号
     * @return 完成后的承保记录；不存在时为空
     */
    public Optional<AutoUnderwritingRecord> completeAutoUnderwriting(String applicationNo) {
        Optional<AutoUnderwritingRecord> completedRecord = markCompleted(applicationNo);
        completedRecord.ifPresent(record -> notifyCompletion(record.notificationRequestId(), applicationNo));
        return completedRecord;
    }

    /**
     * 关闭模拟完成调度线程。
     */
    @PreDestroy
    public void shutdown() {
        completionScheduler.shutdownNow();
    }

    private void scheduleCompletion(AutoUnderwritingRecord record) {
        Duration completionDelay = properties.completionDelay();
        long delayMillis = completionDelay == null ? 0L : Math.max(completionDelay.toMillis(), 0L);
        completionScheduler.schedule(() -> completeAutomatically(record.applicationNo(), record.notificationRequestId()),
                delayMillis, TimeUnit.MILLISECONDS);
    }

    private void completeAutomatically(String applicationNo, String notificationRequestId) {
        markCompleted(applicationNo).ifPresent(record -> {
            if (properties.notifyOnComplete()) {
                notifyCompletion(notificationRequestId, applicationNo);
            }
        });
    }

    private Optional<AutoUnderwritingRecord> markCompleted(String applicationNo) {
        return Optional.ofNullable(autoUnderwritingsByApplicationNo.computeIfPresent(applicationNo,
                (key, record) -> record.status() == UnderwritingResult.COMPLETED
                        ? record
                        : new AutoUnderwritingRecord(applicationNo, UnderwritingResult.COMPLETED,
                                record.notificationRequestId(), record.registeredAt(),
                                LocalDateTime.now(BUSINESS_ZONE_ID))));
    }

    /**
     * 进程内触发承保完成通知，与 HTTP 回调共用同一套幂等规则。
     *
     * @param requestId     通知请求标识
     * @param applicationNo 平台申请编号
     */
    private void notifyCompletion(String requestId, String applicationNo) {
        try {
            underwritingCallbackUseCase.complete(
                    new UnderwritingCompletedCommand(applicationNo, requestId, createTraceId()));
        } catch (RuntimeException exception) {
            log.warn("模拟承保完成通知处理失败，等待补偿任务恢复，applicationNo={}", applicationNo, exception);
        }
    }

    private String createTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 自动承保命令。
     *
     * @param applicationNo        平台申请编号
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     */
    public record AutoUnderwritingCommand(String applicationNo, String tenantCode, String insuredCertificateNo) {
    }

    /**
     * 保单记录。
     *
     * @param certificateNo 被保人证件号
     * @param policyNo      保单号
     * @param registeredAt  登记时间
     */
    public record PolicyRecord(String certificateNo, String policyNo, LocalDateTime registeredAt) {
    }

    /**
     * 保单查询结果。
     *
     * @param hasValidPolicy 是否存在有效保单
     * @param policyNo       保单号
     */
    public record PolicyQueryOutcome(boolean hasValidPolicy, String policyNo) {

        /**
         * 构建无有效保单结果。
         *
         * @return 查询结果
         */
        public static PolicyQueryOutcome absent() {
            return new PolicyQueryOutcome(false, null);
        }

        /**
         * 构建有有效保单结果。
         *
         * @param policyNo 保单号
         * @return 查询结果
         */
        public static PolicyQueryOutcome present(String policyNo) {
            return new PolicyQueryOutcome(true, policyNo);
        }
    }

    /**
     * 自动承保记录。
     *
     * @param applicationNo          平台申请编号
     * @param status                承保状态
     * @param notificationRequestId 完成通知的稳定请求标识
     * @param registeredAt          受理时间
     * @param completedAt           完成时间
     */
    public record AutoUnderwritingRecord(String applicationNo, UnderwritingResult status, String notificationRequestId,
                                         LocalDateTime registeredAt, LocalDateTime completedAt) {
    }
}
