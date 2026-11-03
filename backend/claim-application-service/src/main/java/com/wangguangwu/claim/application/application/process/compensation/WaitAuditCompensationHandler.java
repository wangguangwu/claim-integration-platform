package com.wangguangwu.claim.application.application.process.compensation;

import com.wangguangwu.claim.application.application.command.AuditSignedCommand;
import com.wangguangwu.claim.application.application.port.AuditSystemPort;
import com.wangguangwu.claim.application.application.port.ClaimApplicationCompensationPolicy;
import com.wangguangwu.claim.application.application.port.model.AuditDeliveryRequest;
import com.wangguangwu.claim.application.application.port.model.AuditSignedStatus;
import com.wangguangwu.claim.application.application.port.model.InteractionAction;
import com.wangguangwu.claim.application.application.port.model.InteractionType;
import com.wangguangwu.claim.application.application.process.AuditSignedUseCase;
import com.wangguangwu.claim.application.application.process.ClaimApplicationCompensationTransactionService;
import com.wangguangwu.claim.application.application.process.ClaimApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 等待审核滞留补偿：查询签收状态并按结果完成、保持或重新交付。
 *
 * <p>查询失败、已签收但缺少案件号或签收处理失败时只记录轨迹并保持等待审核，
 * 单条异常不中断同批其他申请。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class WaitAuditCompensationHandler {

    private static final String AUDIT_SIGNED_QUERY_FAILED_CODE = "AUDIT_SIGNED_QUERY_FAILED";
    private static final String AUDIT_SIGNED_CASE_NO_MISSING_CODE = "AUDIT_SIGNED_CASE_NO_MISSING";
    private static final String AUDIT_SIGNED_RECORD_FAILED_CODE = "AUDIT_SIGNED_RECORD_FAILED";
    private static final String AUDIT_DELIVERY_REQUEST_FAILED_CODE = "AUDIT_DELIVERY_REQUEST_FAILED";
    private static final String COMPENSATION_AUDIT_SIGNED_EVENT_PREFIX = "compensation-audit-signed-";

    private final ClaimApplicationRepository claimApplicationRepository;
    private final AuditSystemPort auditSystemPort;
    private final AuditSignedUseCase auditSignedUseCase;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;
    private final ClaimApplicationCompensationTransactionService compensationTransactionService;
    private final ClaimApplicationCompensationPolicy compensationPolicy;

    /**
     * 对等待审核滞留的申请查询签收状态：已签收则推进完成，未找到则重新交付审核系统。
     *
     * @return 尝试补偿的申请条数
     */
    public int recover() {
        LocalDateTime updatedBefore = CompensationDefaults.now().minus(compensationPolicy.waitAuditTimeout());
        List<ClaimApplication> claimApplications = claimApplicationRepository.findByStatusUpdatedBefore(
                ClaimApplicationStatus.WAIT_AUDIT, updatedBefore, compensationPolicy.batchSize());
        for (ClaimApplication claimApplication : claimApplications) {
            recoverWaitAuditClaimApplication(claimApplication);
        }
        if (!claimApplications.isEmpty()) {
            log.info("等待审核滞留补偿执行完成，compensatedCount={}", claimApplications.size());
        }
        return claimApplications.size();
    }

    private void recoverWaitAuditClaimApplication(ClaimApplication claimApplication) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        String traceId = UUID.randomUUID().toString();
        AuditSignedStatus auditSignedStatus;
        try {
            auditSignedStatus = auditSystemPort.querySignedStatus(claimApplicationNo);
        } catch (RuntimeException exception) {
            log.warn("审核签收状态查询失败，等待下一轮补偿，claimApplicationNo={}", claimApplicationNo, exception);
            recordAuditSignedFailure(claimApplication, traceId, AUDIT_SIGNED_QUERY_FAILED_CODE,
                    exception.getMessage());
            return;
        }
        switch (auditSignedStatus) {
            case AuditSignedStatus.Signed signed -> completeAuditSigned(claimApplication, signed, traceId);
            case AuditSignedStatus.Processing ignored -> log.info("审核系统仍在处理，保持等待审核状态，claimApplicationNo={}",
                    claimApplicationNo);
            case AuditSignedStatus.NotFound ignored -> redeliverAuditDelivery(claimApplication, traceId);
        }
    }

    /**
     * 审核系统已签收时复用审核签收用例完成申请，案件号为空按查询失败处理。
     */
    private void completeAuditSigned(ClaimApplication claimApplication, AuditSignedStatus.Signed signed,
                                     String traceId) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        if (signed.caseNos().isEmpty()) {
            log.warn("审核系统已签收但未返回案件号，保持等待审核状态，claimApplicationNo={}", claimApplicationNo);
            recordAuditSignedFailure(claimApplication, traceId, AUDIT_SIGNED_CASE_NO_MISSING_CODE,
                    "审核系统已签收但未返回案件号");
            return;
        }
        try {
            auditSignedUseCase.record(new AuditSignedCommand(claimApplicationNo, claimApplication.getTenantCode(),
                    COMPENSATION_AUDIT_SIGNED_EVENT_PREFIX + UUID.randomUUID(), traceId, signed.caseNos()));
        } catch (RuntimeException exception) {
            log.warn("审核签收补偿推进失败，等待下一轮补偿，claimApplicationNo={}", claimApplicationNo, exception);
            recordAuditSignedFailure(claimApplication, traceId, AUDIT_SIGNED_RECORD_FAILED_CODE,
                    exception.getMessage());
        }
    }

    /**
     * 审核系统未找到申请时重新交付，交付结果写入审核交付轨迹。
     */
    private void redeliverAuditDelivery(ClaimApplication claimApplication, String traceId) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        String interactionNo = recordFactory.newInteractionNo();
        String requestId = CompensationDefaults.requestId(claimApplicationNo);
        try {
            auditSystemPort.deliver(new AuditDeliveryRequest(claimApplicationNo,
                    claimApplication.getTenantCode(), traceId));
            compensationTransactionService.recordAttempt(claimApplicationNo, InteractionType.AUDIT_DELIVERY,
                    interactionNo, requestId, InteractionAction.COMPLETED, traceId, null, null);
        } catch (RuntimeException exception) {
            log.warn("审核交付补偿重发失败，保持等待审核状态，claimApplicationNo={}", claimApplicationNo, exception);
            compensationTransactionService.recordAttempt(claimApplicationNo, InteractionType.AUDIT_DELIVERY,
                    interactionNo, requestId, InteractionAction.FAILED, traceId, AUDIT_DELIVERY_REQUEST_FAILED_CODE,
                    exception.getMessage());
        }
    }

    private void recordAuditSignedFailure(ClaimApplication claimApplication, String traceId, String errorCode,
                                          String errorMessage) {
        compensationTransactionService.recordAttempt(claimApplication.getClaimApplicationNo(),
                InteractionType.AUDIT_SIGNED, recordFactory.newInteractionNo(),
                CompensationDefaults.requestId(claimApplication.getClaimApplicationNo()), InteractionAction.FAILED,
                traceId, errorCode, errorMessage);
    }
}
