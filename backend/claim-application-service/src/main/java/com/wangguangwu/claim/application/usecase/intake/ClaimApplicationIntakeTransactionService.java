package com.wangguangwu.claim.application.usecase.intake;

import com.wangguangwu.claim.application.domain.claim.port.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.integration.outbox.port.OutboxEventRepository;
import com.wangguangwu.claim.application.domain.material.port.MaterialRepository;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeDisposition;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
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

    private final ApplicationRepository applicationRepository;
    private final MaterialRepository materialRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ClaimApplicationIntakeMaterialFactory materialFactory;
    private final ApplicationLifecycleRecordFactory lifecycleRecordFactory;

    /**
     * 在本地事务中完成首次签收、重复复用或受理失败后的重新申请。
     *
     * @param submitted              协议转换后的理赔申请
     * @param existed                存在性提示：true 表示数据库中已有同外部申请号记录
     * @param candidateApplicationNo 存在性提示为 false 时必须提供的候选平台申请编号
     * @param requestId              请求标识
     * @param traceId                链路标识
     * @return 签收结果
     */
    @Transactional
    public ClaimApplicationIntakeResult intake(ClaimApplication submitted, boolean existed,
                                          String candidateApplicationNo, String requestId, String traceId) {
        if (!existed) {
            return create(submitted, candidateApplicationNo, requestId, traceId);
        }
        return applicationRepository.findByTenantAndExternalApplicationNo(submitted.getTenantCode(),
                        submitted.getExternalApplicationNo())
                .map(application -> processExisting(application, submitted, requestId, traceId))
                .orElseThrow(ApplicationExistenceMismatchException::new);
    }

    /**
     * 唯一键冲突后在新事务中解析同一外部申请号是否已由并发请求创建。
     *
     * @param submitted 协议转换后的理赔申请
     * @param requestId 请求标识
     * @param traceId   链路标识
     * @return 已存在申请的结果；不存在时返回空
     */
    @Transactional
    public Optional<ClaimApplicationIntakeResult> resolveAfterDuplicateKey(ClaimApplication submitted,
                                                                      String requestId, String traceId) {
        return applicationRepository.findByTenantAndExternalApplicationNo(submitted.getTenantCode(),
                        submitted.getExternalApplicationNo())
                .map(application -> processExisting(application, submitted, requestId, traceId));
    }

    /**
     * 创建首次理赔申请、材料、交互日志与 Outbox 事件。
     *
     * @param submitted                协议转换后的理赔申请
     * @param candidateApplicationNo   候选平台申请编号
     * @param requestId                请求标识
     * @param traceId                  链路标识
     * @return 新建结果
     */
    private ClaimApplicationIntakeResult create(ClaimApplication submitted, String candidateApplicationNo,
                                           String requestId, String traceId) {
        submitted.bindApplicationNo(candidateApplicationNo);
        applicationRepository.insert(submitted);

        String interactionNo = lifecycleRecordFactory.newInteractionNo();
        recordStarted(candidateApplicationNo, interactionNo, requestId, traceId);
        materialRepository.insertAll(materialFactory.createMaterials(candidateApplicationNo, submitted.getMaterials()));
        outboxEventRepository.insert(lifecycleRecordFactory.applicationCreatedEvent(submitted, traceId));
        recordFinished(candidateApplicationNo, interactionNo, requestId, traceId, true);
        return new ClaimApplicationIntakeResult(candidateApplicationNo, ClaimApplicationIntakeDisposition.CREATED);
    }

    /**
     * 根据当前状态处理重复请求或重新申请。
     *
     * <p>重新申请使用状态条件更新：并发请求已先完成重新申请时更新行数为 0，
     * 本次请求按重复请求复用已有申请，不覆盖对方写入的人员与材料。</p>
     *
     * @param application 当前理赔申请
     * @param submitted   协议转换后的理赔申请
     * @param requestId   请求标识
     * @param traceId     链路标识
     * @return 复用或重新申请结果
     */
    private ClaimApplicationIntakeResult processExisting(ClaimApplication application, ClaimApplication submitted,
                                                    String requestId, String traceId) {
        String applicationNo = application.getApplicationNo();
        String interactionNo = lifecycleRecordFactory.newInteractionNo();
        recordStarted(applicationNo, interactionNo, requestId, traceId);
        if (application.getStatus() != ApplicationStatus.ACCEPT_FAILED) {
            recordFinished(applicationNo, interactionNo, requestId, traceId, false);
            return new ClaimApplicationIntakeResult(applicationNo, ClaimApplicationIntakeDisposition.REUSED);
        }

        application.reapply(submitted);
        if (!applicationRepository.updateForReapply(application)) {
            recordFinished(applicationNo, interactionNo, requestId, traceId, false);
            return new ClaimApplicationIntakeResult(applicationNo, ClaimApplicationIntakeDisposition.REUSED);
        }

        materialRepository.softDeleteByApplicationNo(applicationNo);
        materialRepository.insertAll(materialFactory.createMaterials(applicationNo, submitted.getMaterials()));
        outboxEventRepository.insert(lifecycleRecordFactory.applicationReappliedEvent(application, traceId));
        recordFinished(applicationNo, interactionNo, requestId, traceId, true);
        return new ClaimApplicationIntakeResult(applicationNo, ClaimApplicationIntakeDisposition.REAPPLIED);
    }

    private void recordStarted(String applicationNo, String interactionNo, String requestId, String traceId) {
        record(applicationNo, interactionNo, InteractionAction.STARTED, requestId, traceId);
    }

    private void recordFinished(String applicationNo, String interactionNo, String requestId, String traceId,
                               boolean completed) {
        record(applicationNo, interactionNo,
                completed ? InteractionAction.COMPLETED : InteractionAction.REUSED, requestId, traceId);
    }

    private void record(String applicationNo, String interactionNo, InteractionAction action, String requestId,
                        String traceId) {
        interactionLogRepository.insert(lifecycleRecordFactory.interaction(InteractionType.APPLICATION_INTAKE,
                applicationNo, interactionNo, action, requestId, traceId, null, null));
    }
}
