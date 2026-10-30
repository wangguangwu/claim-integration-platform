package com.wangguangwu.claim.application.usecase.intake;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeLookupKind;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeSnapshot;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.integration.outbox.port.OutboxEventRepository;
import com.wangguangwu.claim.application.domain.material.port.MaterialRepository;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeDisposition;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 申请签收的数据库事务服务，只负责持久化编排，不访问 Redis。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationIntakeTransactionService {

    private final ClaimApplicationRepository claimApplicationRepository;
    private final MaterialRepository materialRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ClaimApplicationIntakeMaterialFactory materialFactory;
    private final ClaimApplicationLifecycleRecordFactory lifecycleRecordFactory;

    /**
     * 在本地事务中完成首次签收、重复复用或受理失败后的重新申请。
     *
     * <p>存在但状态可变时读到的聚合状态即是回源观察结论：安全状态随结果返回可复用快照，
     * 供用例在事务提交后升级判定缓存，避免同一申请号反复回源。</p>
     *
     * @param submitted                   协议转换后的理赔申请
     * @param lookupKind                  签收判定结论：不存在时创建，存在时读取聚合判定
     * @param candidateClaimApplicationNo 判定为不存在时必须提供的候选平台申请编号
     * @param requestId                   请求标识
     * @param traceId                     链路标识
     * @return 签收结果与回源观察到的可复用快照
     */
    @Transactional
    public ClaimApplicationIntakeOutcome intake(ClaimApplication submitted, ClaimApplicationIntakeLookupKind lookupKind,
                                                String candidateClaimApplicationNo, String requestId, String traceId) {
        if (lookupKind == ClaimApplicationIntakeLookupKind.ABSENT) {
            return ClaimApplicationIntakeOutcome.withoutSnapshot(
                    create(submitted, candidateClaimApplicationNo, requestId, traceId));
        }
        return claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(submitted.getTenantCode(),
                        submitted.getExternalClaimApplicationNo())
                .map(claimApplication -> processExisting(claimApplication, submitted, requestId, traceId))
                .orElseThrow(ClaimApplicationExistenceMismatchException::new);
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
    public Optional<ClaimApplicationIntakeOutcome> resolveAfterDuplicateKey(ClaimApplication submitted,
                                                                          String requestId, String traceId) {
        return claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(submitted.getTenantCode(),
                        submitted.getExternalClaimApplicationNo())
                .map(claimApplication -> processExisting(claimApplication, submitted, requestId, traceId));
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
        materialRepository.insertAll(materialFactory.createMaterials(candidateClaimApplicationNo, submitted.getMaterials()));
        outboxEventRepository.insert(lifecycleRecordFactory.claimApplicationCreatedEvent(submitted, traceId));
        recordFinished(candidateClaimApplicationNo, interactionNo, requestId, traceId, true);
        return new ClaimApplicationIntakeResult(candidateClaimApplicationNo, ClaimApplicationIntakeDisposition.CREATED);
    }

    /**
     * 根据当前状态处理重复请求或重新申请。
     *
     * <p>重新申请使用状态条件更新：并发请求已先完成重新申请时更新行数为 0，
     * 本次请求按重复请求复用已有申请，不覆盖对方写入的人员与材料。</p>
     *
     * @param claimApplication 当前理赔申请
     * @param submitted   协议转换后的理赔申请
     * @param requestId   请求标识
     * @param traceId     链路标识
     * @return 复用或重新申请结果，以及读到的可复用快照
     */
    private ClaimApplicationIntakeOutcome processExisting(ClaimApplication claimApplication,
                                                         ClaimApplication submitted,
                                                         String requestId, String traceId) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        String interactionNo = lifecycleRecordFactory.newInteractionNo();
        recordStarted(claimApplicationNo, interactionNo, requestId, traceId);
        if (claimApplication.getStatus() != ClaimApplicationStatus.ACCEPT_FAILED) {
            recordFinished(claimApplicationNo, interactionNo, requestId, traceId, false);
            return outcome(new ClaimApplicationIntakeResult(claimApplicationNo, ClaimApplicationIntakeDisposition.REUSED),
                    claimApplication);
        }

        claimApplication.reapply(submitted);
        if (!claimApplicationRepository.updateForReapply(claimApplication)) {
            recordFinished(claimApplicationNo, interactionNo, requestId, traceId, false);
            return ClaimApplicationIntakeOutcome.withoutSnapshot(
                    new ClaimApplicationIntakeResult(claimApplicationNo, ClaimApplicationIntakeDisposition.REUSED));
        }

        materialRepository.softDeleteByClaimApplicationNo(claimApplicationNo);
        materialRepository.insertAll(materialFactory.createMaterials(claimApplicationNo, submitted.getMaterials()));
        outboxEventRepository.insert(lifecycleRecordFactory.claimApplicationReappliedEvent(claimApplication, traceId));
        recordFinished(claimApplicationNo, interactionNo, requestId, traceId, true);
        return ClaimApplicationIntakeOutcome.withoutSnapshot(
                new ClaimApplicationIntakeResult(claimApplicationNo, ClaimApplicationIntakeDisposition.REAPPLIED));
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
        if (!ClaimApplicationIntakeSnapshot.isReusableStatus(status)) {
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
