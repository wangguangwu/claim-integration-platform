package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.application.exception.ApplicationException;
import com.wangguangwu.claim.application.application.exception.ClaimApplicationIntakeConflictException;
import com.wangguangwu.claim.application.application.port.model.InteractionAction;
import com.wangguangwu.claim.application.application.port.model.InteractionType;
import com.wangguangwu.claim.application.application.port.InteractionLogRepository;
import com.wangguangwu.claim.application.application.port.OutboxEventRepository;
import com.wangguangwu.claim.application.application.port.model.ClaimApplicationIntakeLookupKind;
import com.wangguangwu.claim.application.application.port.model.ClaimApplicationIntakeSnapshot;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 申请签收的数据库事务服务，只负责持久化编排，不访问 Redis。
 *
 * <p>首次签收、重新申请与租户补材都在同一事务内完成聚合落库、交互轨迹与 Outbox 写入；
 * 聚合保存失败时抛出内部冲突信号让整个事务回滚，交由用例在新事务中重新分类。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationIntakeTransactionService {

    /** 业务时间统一使用东八区，与数据库时间字段口径一致。 */
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ClaimApplicationRepository claimApplicationRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ClaimApplicationLifecycleRecordFactory lifecycleRecordFactory;

    /**
     * 未携带平台申请编号时的签收：不存在则创建，存在一律按重复请求复用。
     *
     * @param submitted                   协议转换后的理赔申请
     * @param lookupKind                  签收判定结论：不存在时创建，存在时读取后复用
     * @param candidateClaimApplicationNo 判定为不存在时必须提供的候选平台申请编号
     * @param requestId                   请求标识
     * @param traceId                     链路标识
     * @return 签收结果与回源观察到的可复用快照；存在性不一致时返回空
     */
    @Transactional
    public Optional<ClaimApplicationIntakeOutcome> intake(ClaimApplication submitted,
                                                          ClaimApplicationIntakeLookupKind lookupKind,
                                                          String candidateClaimApplicationNo, String requestId,
                                                          String traceId) {
        if (lookupKind == ClaimApplicationIntakeLookupKind.ABSENT) {
            return Optional.of(ClaimApplicationIntakeOutcome.withoutSnapshot(
                    create(submitted, candidateClaimApplicationNo, requestId, traceId)));
        }
        return claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(submitted.getTenantCode(),
                        submitted.getExternalClaimApplicationNo())
                .map(claimApplication -> processExistingAsReused(claimApplication, requestId, traceId));
    }

    /**
     * 携带平台申请编号时的签收：严格校验编号后按状态重新申请、补材或复用。
     *
     * @param submitted                   协议转换后的理赔申请
     * @param expectedClaimApplicationNo  报文携带的平台申请编号
     * @param batchNo                     本次提交生成的材料批次编号
     * @param requestId                   请求标识
     * @param traceId                     链路标识
     * @return 签收结果；其余状态按重复请求复用
     * @throws ApplicationException 编号与库中记录不一致时拒绝签收
     */
    @Transactional
    public ClaimApplicationIntakeOutcome intakeExisting(ClaimApplication submitted, String expectedClaimApplicationNo,
                                                        String batchNo, String requestId, String traceId) {
        ClaimApplication claimApplication = claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(
                        submitted.getTenantCode(), submitted.getExternalClaimApplicationNo())
                .orElseThrow(() -> new ApplicationException("平台申请编号无法匹配既有申请，tenantCode=" + submitted.getTenantCode()
                                + "，externalClaimApplicationNo=" + submitted.getExternalClaimApplicationNo()));
        if (!expectedClaimApplicationNo.equals(claimApplication.getClaimApplicationNo())) {
            throw new ApplicationException("报文平台申请编号与既有申请不一致，externalClaimApplicationNo="
                            + submitted.getExternalClaimApplicationNo()
                            + "，报文编号=" + expectedClaimApplicationNo
                            + "，实际编号=" + claimApplication.getClaimApplicationNo());
        }
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        int attemptBefore = claimApplication.getClaimApplicationAttempt();
        String interactionNo = lifecycleRecordFactory.newInteractionNo();
        recordStarted(claimApplicationNo, interactionNo, requestId, traceId);
        if (claimApplication.isReapplicable()) {
            claimApplication.reapply(submitted, batchNo, LocalDateTime.now(BUSINESS_ZONE_ID));
            saveOrConflict(claimApplication, attemptBefore, false);
            outboxEventRepository.insert(lifecycleRecordFactory.claimApplicationReappliedEvent(claimApplication, traceId));
            recordFinished(claimApplicationNo, interactionNo, requestId, traceId, true);
            return ClaimApplicationIntakeOutcome.withoutSnapshot(new ClaimApplicationIntakeResult(claimApplicationNo,
                    ClaimApplicationIntakeDisposition.REAPPLIED));
        }
        if (claimApplication.isSupplementable()) {
            claimApplication.supplement(submitted, batchNo, LocalDateTime.now(BUSINESS_ZONE_ID));
            saveOrConflict(claimApplication, attemptBefore, true);
            outboxEventRepository.insert(lifecycleRecordFactory.claimApplicationReappliedEvent(claimApplication, traceId));
            recordFinished(claimApplicationNo, interactionNo, requestId, traceId, true);
            return ClaimApplicationIntakeOutcome.withoutSnapshot(new ClaimApplicationIntakeResult(claimApplicationNo,
                    ClaimApplicationIntakeDisposition.SUPPLEMENTED));
        }
        recordFinished(claimApplicationNo, interactionNo, requestId, traceId, false);
        return outcome(new ClaimApplicationIntakeResult(claimApplicationNo, ClaimApplicationIntakeDisposition.REUSED),
                claimApplication);
    }

    /**
     * 唯一键冲突后在新事务中解析同一外部申请号是否已由并发请求创建。
     *
     * @param submitted 协议转换后的理赔申请
     * @param requestId 请求标识
     * @param traceId   链路标识
     * @return 已存在申请的签收产物；不存在时返回空
     */
    @Transactional
    public Optional<ClaimApplicationIntakeOutcome> resolveAfterKeyConflict(ClaimApplication submitted,
                                                                          String requestId, String traceId) {
        return claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(submitted.getTenantCode(),
                        submitted.getExternalClaimApplicationNo())
                .map(claimApplication -> processExistingAsReused(claimApplication, requestId, traceId));
    }

    /**
     * 命中可复用快照时只写交互轨迹，不读取聚合、不更新业务数据。
     *
     * @param claimApplicationNo 平台申请编号
     * @param requestId          请求标识
     * @param traceId            链路标识
     * @return 复用结果
     */
    @Transactional
    public ClaimApplicationIntakeResult recordReused(String claimApplicationNo, String requestId, String traceId) {
        String interactionNo = lifecycleRecordFactory.newInteractionNo();
        recordStarted(claimApplicationNo, interactionNo, requestId, traceId);
        recordFinished(claimApplicationNo, interactionNo, requestId, traceId, false);
        return new ClaimApplicationIntakeResult(claimApplicationNo, ClaimApplicationIntakeDisposition.REUSED);
    }

    /**
     * 创建首次理赔申请、材料、交互日志与 Outbox 事件。
     *
     * @param submitted                协议转换后的理赔申请
     * @param candidateClaimApplicationNo   候选平台申请编号
     * @param requestId                请求标识
     * @param traceId                  链路标识
     * @return 新建结果
     */
    private ClaimApplicationIntakeResult create(ClaimApplication submitted, String candidateClaimApplicationNo,
                                           String requestId, String traceId) {
        submitted.bindClaimApplicationNo(candidateClaimApplicationNo);
        claimApplicationRepository.insert(submitted);

        String interactionNo = lifecycleRecordFactory.newInteractionNo();
        recordStarted(candidateClaimApplicationNo, interactionNo, requestId, traceId);
        outboxEventRepository.insert(lifecycleRecordFactory.claimApplicationCreatedEvent(submitted, traceId));
        recordFinished(candidateClaimApplicationNo, interactionNo, requestId, traceId, true);
        return new ClaimApplicationIntakeResult(candidateClaimApplicationNo, ClaimApplicationIntakeDisposition.CREATED);
    }

    /**
     * 未携带平台编号时，既有申请不修改人员、材料与状态，只按重复请求复用。
     *
     * @param claimApplication 已存在的理赔申请
     * @param requestId        请求标识
     * @param traceId          链路标识
     * @return 复用结果，以及读到的可复用快照
     */
    private ClaimApplicationIntakeOutcome processExistingAsReused(ClaimApplication claimApplication, String requestId,
                                                                  String traceId) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        String interactionNo = lifecycleRecordFactory.newInteractionNo();
        recordStarted(claimApplicationNo, interactionNo, requestId, traceId);
        recordFinished(claimApplicationNo, interactionNo, requestId, traceId, false);
        return outcome(new ClaimApplicationIntakeResult(claimApplicationNo, ClaimApplicationIntakeDisposition.REUSED),
                claimApplication);
    }

    /**
     * 保存聚合，版本过期时抛出内部冲突信号使本次事务回滚。
     *
     * @param claimApplication 已变更的聚合
     * @param attemptBefore    变更前的受理尝试次数
     * @param supplement       本次是否为租户补材
     */
    private void saveOrConflict(ClaimApplication claimApplication, int attemptBefore, boolean supplement) {
        if (!claimApplicationRepository.save(claimApplication)) {
            throw new ClaimApplicationIntakeConflictException(claimApplication.getClaimApplicationNo(), attemptBefore,
                    supplement);
        }
    }

    /**
     * 状态属于安全集合时随结果带出可复用快照。
     *
     * @param result           复用结果
     * @param claimApplication 回源读到的理赔申请
     * @return 带或不带快照的产物
     */
    private ClaimApplicationIntakeOutcome outcome(ClaimApplicationIntakeResult result,
                                                  ClaimApplication claimApplication) {
        ClaimApplicationStatus status = claimApplication.getStatus();
        if (!status.isReusableAfterIntake()) {
            return ClaimApplicationIntakeOutcome.withoutSnapshot(result);
        }
        return ClaimApplicationIntakeOutcome.withSnapshot(result,
                new ClaimApplicationIntakeSnapshot(claimApplication.getClaimApplicationNo(), status));
    }

    private void recordStarted(String claimApplicationNo, String interactionNo, String requestId, String traceId) {
        record(claimApplicationNo, interactionNo, InteractionAction.STARTED, requestId, traceId);
    }

    private void recordFinished(String claimApplicationNo, String interactionNo, String requestId, String traceId,
                               boolean completed) {
        record(claimApplicationNo, interactionNo,
                completed ? InteractionAction.COMPLETED : InteractionAction.REUSED, requestId, traceId);
    }

    private void record(String claimApplicationNo, String interactionNo, InteractionAction action, String requestId,
                        String traceId) {
        interactionLogRepository.insert(lifecycleRecordFactory.interaction(InteractionType.CLAIM_APPLICATION_INTAKE,
                claimApplicationNo, interactionNo, action, requestId, traceId, null, null));
    }
}
