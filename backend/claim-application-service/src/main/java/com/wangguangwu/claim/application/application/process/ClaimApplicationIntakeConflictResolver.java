package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.application.exception.ApplicationException;
import com.wangguangwu.claim.application.application.port.model.InteractionAction;
import com.wangguangwu.claim.application.application.port.model.InteractionType;
import com.wangguangwu.claim.application.application.port.InteractionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 签收保存冲突的独立解析入口，必须在保存失败事务之外以新事务重读聚合。
 *
 * <p>MySQL 默认 {@code REPEATABLE_READ} 下，保存失败的原事务内重读仍会返回旧快照，
 * 因此分类前必须开启新事务：尝试次数已增大说明并发同类请求成功，按复用返回；
 * 补材场景状态已离开问题件说明审核系统已关闭问题件；其余情况仍按处理中要求租户重试。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationIntakeConflictResolver {

    private final ClaimApplicationRepository claimApplicationRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final ClaimApplicationLifecycleRecordFactory lifecycleRecordFactory;

    /**
     * 在新事务中重读聚合并对保存冲突分类。
     *
     * @param tenantCode                  租户编码
     * @param externalClaimApplicationNo  外部申请编号
     * @param claimApplicationNo          本次签收的平台申请编号
     * @param attemptBefore               调用聚合变更前的受理尝试次数
     * @param supplement                  本次是否为租户补材
     * @param requestId                   请求标识
     * @param traceId                     链路标识
     * @return 并发同类请求已成功时的复用结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimApplicationIntakeResult resolve(String tenantCode, String externalClaimApplicationNo,
                                                String claimApplicationNo, int attemptBefore, boolean supplement,
                                                String requestId, String traceId) {
        ClaimApplication current = claimApplicationRepository.findByTenantAndExternalClaimApplicationNo(tenantCode,
                        externalClaimApplicationNo)
                .orElseThrow(() -> new ApplicationException(
                        ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_BUSY,
                        "理赔申请处理冲突且无法重读，claimApplicationNo=" + claimApplicationNo));
        if (current.getClaimApplicationAttempt() > attemptBefore) {
            recordReused(current.getClaimApplicationNo(), requestId, traceId);
            return new ClaimApplicationIntakeResult(current.getClaimApplicationNo(),
                    ClaimApplicationIntakeDisposition.REUSED);
        }
        if (supplement && current.getStatus() != ClaimApplicationStatus.ISSUE) {
            throw new ApplicationException(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID,
                    "补材时申请状态已变化，claimApplicationNo=" + claimApplicationNo
                            + "，当前状态=" + current.getStatus());
        }
        throw new ApplicationException(ClaimApplicationIntakeErrorCode.CLAIM_APPLICATION_INTAKE_BUSY,
                "理赔申请正在处理中，claimApplicationNo=" + claimApplicationNo);
    }

    private void recordReused(String claimApplicationNo, String requestId, String traceId) {
        String interactionNo = lifecycleRecordFactory.newInteractionNo();
        interactionLogRepository.insert(lifecycleRecordFactory.interaction(InteractionType.CLAIM_APPLICATION_INTAKE,
                claimApplicationNo, interactionNo, InteractionAction.STARTED, requestId, traceId, null, null));
        interactionLogRepository.insert(lifecycleRecordFactory.interaction(InteractionType.CLAIM_APPLICATION_INTAKE,
                claimApplicationNo, interactionNo, InteractionAction.REUSED, requestId, traceId, null, null));
    }
}
