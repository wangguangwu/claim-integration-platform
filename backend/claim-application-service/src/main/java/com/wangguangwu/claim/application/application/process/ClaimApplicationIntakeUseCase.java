package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.application.exception.ApplicationException;
import com.wangguangwu.claim.application.application.exception.ClaimApplicationIntakeConflictException;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.application.port.model.ClaimApplicationIntakeLookup;
import com.wangguangwu.claim.application.application.port.model.ClaimApplicationIntakeLookupKind;
import com.wangguangwu.claim.application.application.port.ClaimApplicationIntakeLock;
import com.wangguangwu.claim.application.application.port.ClaimApplicationIntakePolicy;
import com.wangguangwu.claim.application.application.port.ClaimApplicationIntakeSnapshotQuery;
import com.wangguangwu.claim.application.application.port.ClaimApplicationNoGenerator;
import com.wangguangwu.claim.application.shared.exception.RepositoryConflictException;
import com.wangguangwu.claim.foundation.observability.flow.FlowLog;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 理赔申请统一接入签收用例入口，负责并发锁、事务外准备数据与冲突恢复。
 *
 * <p>未携带平台申请编号时按外部申请编号判定：可复用快照只写轨迹，库中无记录则创建，
 * 已有记录一律复用；携带平台申请编号时跳过快照直接读库并严格比对，只有受理失败与未关闭问题件
 * 才分别触发重新申请与租户补材。保存冲突由独立入口在新事务中重读分类。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationIntakeUseCase {

    private static final String BATCH_NO_PREFIX = "B";
    private static final DateTimeFormatter BATCH_NO_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ClaimApplicationIntakeLock claimApplicationIntakeLock;
    private final ClaimApplicationIntakeSnapshotQuery claimApplicationIntakeSnapshotQuery;
    private final ClaimApplicationNoGenerator claimApplicationNoGenerator;
    private final ClaimApplicationIntakePolicy claimApplicationIntakePolicy;
    private final ClaimApplicationIntakeTransactionService transactionService;
    private final ClaimApplicationIntakeConflictResolver conflictResolver;

    /**
     * 创建理赔申请，并在同一外部申请号锁内完成签收。
     *
     * @param command   租户协议归一化后的受理命令
     * @param requestId 请求标识
     * @param traceId   链路标识
     * @return 签收结果
     */
    @FlowLog("理赔申请签收")
    public ClaimApplicationIntakeResult intake(ClaimApplicationIntakeCommand command, String requestId, String traceId) {
        String batchNo = nextBatchNo();
        ClaimApplication submitted = ClaimApplication.submit(command.tenantCode(), command.externalClaimApplicationNo(),
                command.applicant(), command.primaryInsured(), command.accidentPerson(), command.payee(),
                command.materials(), batchNo);
        String expectedClaimApplicationNo = normalize(command.claimApplicationNo());
        return claimApplicationIntakeLock.execute(submitted.getTenantCode(),
                submitted.getExternalClaimApplicationNo(),
                () -> expectedClaimApplicationNo == null
                        ? executeWithoutPlatformNumber(submitted, requestId, traceId)
                        : executeWithPlatformNumber(submitted, expectedClaimApplicationNo, batchNo, requestId, traceId));
    }

    /**
     * 携带平台申请编号的签收：跳过快照，严格比对后按状态分流，保存冲突在新事务中重读分类。
     *
     * @param submitted                 待签收的理赔申请
     * @param expectedClaimApplicationNo 报文携带的平台申请编号
     * @param batchNo                   本次提交的材料批次编号
     * @param requestId                 请求标识
     * @param traceId                   链路标识
     * @return 签收结果
     */
    private ClaimApplicationIntakeResult executeWithPlatformNumber(ClaimApplication submitted,
                                                                   String expectedClaimApplicationNo, String batchNo,
                                                                   String requestId, String traceId) {
        try {
            ClaimApplicationIntakeOutcome outcome = transactionService.intakeExisting(submitted,
                    expectedClaimApplicationNo, batchNo, requestId, traceId);
            if (outcome.result().disposition() != ClaimApplicationIntakeDisposition.REUSED) {
                markMutable(submitted);
            }
            return upgradedResult(submitted, outcome);
        } catch (ClaimApplicationIntakeConflictException conflict) {
            return conflictResolver.resolve(submitted.getTenantCode(),
                    submitted.getExternalClaimApplicationNo(), conflict.claimApplicationNo(),
                    conflict.attemptBefore(), conflict.supplement(), requestId, traceId);
        }
    }

    /**
     * 未携带平台申请编号的签收：可复用快照只写轨迹，不存在则创建，存在则读取后复用。
     *
     * @param submitted 待签收的理赔申请
     * @param requestId 请求标识
     * @param traceId   链路标识
     * @return 签收结果
     */
    private ClaimApplicationIntakeResult executeWithoutPlatformNumber(ClaimApplication submitted, String requestId,
                                                                      String traceId) {
        boolean forceCreate = false;
        for (int retryTimes = 0; retryTimes < claimApplicationIntakePolicy.uniqueKeyRetryTimes(); retryTimes++) {
            try {
                ClaimApplicationIntakeLookup lookup = forceCreate
                        ? ClaimApplicationIntakeLookup.absent()
                        : claimApplicationIntakeSnapshotQuery.lookup(submitted.getTenantCode(),
                                submitted.getExternalClaimApplicationNo());
                if (lookup.kind() == ClaimApplicationIntakeLookupKind.REUSABLE) {
                    return transactionService.recordReused(lookup.snapshot().claimApplicationNo(), requestId, traceId);
                }
                boolean absent = lookup.kind() == ClaimApplicationIntakeLookupKind.ABSENT;
                String candidateClaimApplicationNo = absent ? claimApplicationNoGenerator.nextClaimApplicationNo() : null;
                Optional<ClaimApplicationIntakeOutcome> outcome = transactionService.intake(submitted,
                        lookup.kind(), candidateClaimApplicationNo, requestId, traceId);
                if (outcome.isEmpty()) {
                    forceCreate = true;
                    continue;
                }
                if (absent) {
                    markMutable(submitted);
                }
                return upgradedResult(submitted, outcome.get());
            } catch (RepositoryConflictException exception) {
                Optional<ClaimApplicationIntakeOutcome> resolvedOutcome = transactionService.resolveAfterKeyConflict(
                        submitted, requestId, traceId);
                if (resolvedOutcome.isPresent()) {
                    markMutable(submitted);
                    return upgradedResult(submitted, resolvedOutcome.get());
                }
                claimApplicationNoGenerator.synchronizeDailyCounter();
                forceCreate = false;
            }
        }
        throw new ApplicationException("理赔申请唯一键冲突重试次数超限，tenantCode=" + submitted.getTenantCode()
                        + "，externalClaimApplicationNo=" + submitted.getExternalClaimApplicationNo());
    }

    /**
     * 返回对外结果，并顺带把事务内观察到的可复用快照写入判定缓存。
     *
     * @param submitted 本次签收的理赔申请
     * @param outcome   事务产物
     * @return 对外签收结果
     */
    private ClaimApplicationIntakeResult upgradedResult(ClaimApplication submitted, ClaimApplicationIntakeOutcome outcome) {
        outcome.reusableSnapshot().ifPresent(snapshot -> claimApplicationIntakeSnapshotQuery.markReusable(
                submitted.getTenantCode(), submitted.getExternalClaimApplicationNo(), snapshot));
        return outcome.result();
    }

    /**
     * 把新创建或并发已创建的申请标记为存在且状态可变，覆盖之前的空标记。
     *
     * @param submitted 已存在的理赔申请
     */
    private void markMutable(ClaimApplication submitted) {
        claimApplicationIntakeSnapshotQuery.markMutable(submitted.getTenantCode(),
                submitted.getExternalClaimApplicationNo());
    }

    /**
     * 生成材料批次编号：业务日期加 UUID 随机段，仅作为本次提交的批次标识。
     *
     * @return 材料批次编号
     */
    private String nextBatchNo() {
        return BATCH_NO_PREFIX + LocalDate.now(BUSINESS_ZONE_ID).format(BATCH_NO_DATE_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 空串按未携带平台申请编号处理。
     *
     * @param claimApplicationNo 报文携带的平台申请编号
     * @return 去除首尾空白后的编号；未携带时为空
     */
    private String normalize(String claimApplicationNo) {
        if (claimApplicationNo == null || claimApplicationNo.isBlank()) {
            return null;
        }
        return claimApplicationNo.trim();
    }
}
