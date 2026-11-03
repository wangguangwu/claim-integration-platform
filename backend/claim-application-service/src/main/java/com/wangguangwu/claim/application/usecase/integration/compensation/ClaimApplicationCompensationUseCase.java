package com.wangguangwu.claim.application.usecase.integration.compensation;

import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.usecase.audit.AuditSignedUseCase;
import com.wangguangwu.claim.application.usecase.audit.command.AuditSignedCommand;
import com.wangguangwu.claim.application.usecase.audit.model.AuditDeliveryRequest;
import com.wangguangwu.claim.application.usecase.audit.model.AuditSignedStatus;
import com.wangguangwu.claim.application.usecase.audit.port.AuditSignedStatusQuery;
import com.wangguangwu.claim.application.usecase.audit.port.ServiceAuditSystemPort;
import com.wangguangwu.claim.application.usecase.integration.compensation.port.ClaimApplicationCompensationPolicy;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionAction;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionType;
import com.wangguangwu.claim.application.usecase.integration.outbox.port.OutboxEventRepository;
import com.wangguangwu.claim.application.usecase.underwriting.port.AutoUnderwritingRequester;
import com.wangguangwu.claim.application.usecase.underwriting.model.UnderwritingResult;
import com.wangguangwu.claim.application.usecase.underwriting.port.UnderwritingResultQuery;
import com.wangguangwu.claim.application.usecase.underwriting.command.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.material.MaterialProcessingUseCase;
import com.wangguangwu.claim.application.usecase.material.command.MaterialProcessCommand;
import com.wangguangwu.claim.application.usecase.underwriting.UnderwritingCallbackUseCase;
import com.wangguangwu.claim.application.usecase.underwriting.UnderwritingUseCase;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 补偿用例：恢复投递滞留、待受理滞留、已受理滞留、承保中滞留与等待审核滞留，仅在状态仍符合预期时推进。
 *
 * <p>补偿重放的开始与结束轨迹都由 {@link ClaimApplicationCompensationTransactionService}
 * 一次性写入，重放中断时不会留下只有开始的行为组。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ClaimApplicationCompensationUseCase {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final String UNDERWRITING_RESULT_QUERY_FAILED_CODE = "UNDERWRITING_RESULT_QUERY_FAILED";
    private static final String AUTO_UNDERWRITING_REQUEST_FAILED_CODE = "AUTO_UNDERWRITING_REQUEST_FAILED";
    private static final String AUDIT_SIGNED_QUERY_FAILED_CODE = "AUDIT_SIGNED_QUERY_FAILED";
    private static final String AUDIT_SIGNED_CASE_NO_MISSING_CODE = "AUDIT_SIGNED_CASE_NO_MISSING";
    private static final String AUDIT_SIGNED_RECORD_FAILED_CODE = "AUDIT_SIGNED_RECORD_FAILED";
    private static final String AUDIT_DELIVERY_REQUEST_FAILED_CODE = "AUDIT_DELIVERY_REQUEST_FAILED";
    private static final String COMPENSATION_REQUEST_ID_PREFIX = "compensation-";
    private static final String COMPENSATION_AUDIT_SIGNED_EVENT_PREFIX = "compensation-audit-signed-";

    private final OutboxEventRepository outboxEventRepository;
    private final ClaimApplicationRepository claimApplicationRepository;
    private final AuditSignedStatusQuery auditSignedStatusQuery;
    private final ServiceAuditSystemPort serviceAuditSystemPort;
    private final UnderwritingUseCase underwritingUseCase;
    private final MaterialProcessingUseCase materialProcessingUseCase;
    private final UnderwritingCallbackUseCase underwritingCallbackUseCase;
    private final UnderwritingResultQuery underwritingResultQuery;
    private final AutoUnderwritingRequester autoUnderwritingRequester;
    private final AuditSignedUseCase auditSignedUseCase;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;
    private final ClaimApplicationCompensationTransactionService compensationTransactionService;
    private final ClaimApplicationCompensationPolicy compensationPolicy;

    /**
     * 把停留过久的发送中事件重置为待投递，处理投递进程崩溃场景。
     *
     * @return 重置条数
     */
    public int resetStuckSendingEvents() {
        LocalDateTime sendingBefore = now().minus(compensationPolicy.outboxSendingTimeout());
        int resetCount = outboxEventRepository.resetStuckSending(sendingBefore);
        if (resetCount > 0) {
            log.info("Outbox 发送滞留事件已重置为待投递，resetCount={}，sendingBefore={}", resetCount,
                    sendingBefore);
        }
        return resetCount;
    }

    /**
     * 对待受理滞留的申请重放材料处理，单条失败不影响同批其他申请。
     *
     * <p>事件标识由申请编号与尝试次数组成，失败回滚后下一轮沿用同一标识重试；
     * 与真实消息并发时由条件更新与消费登记保证只有一次生效。</p>
     *
     * @return 尝试补偿的申请条数
     */
    public int redrivePendingAcceptClaimApplications() {
        LocalDateTime updatedBefore = now().minus(compensationPolicy.pendingAcceptTimeout());
        List<ClaimApplication> claimApplications = claimApplicationRepository.findByStatusUpdatedBefore(
                ClaimApplicationStatus.PENDING_ACCEPT, updatedBefore, compensationPolicy.batchSize());
        for (ClaimApplication claimApplication : claimApplications) {
            try {
                materialProcessingUseCase.process(new MaterialProcessCommand(claimApplication.getClaimApplicationNo(),
                        claimApplication.getTenantCode(), compensationEventId(claimApplication),
                        UUID.randomUUID().toString(), claimApplication.getClaimApplicationAttempt()));
            } catch (RuntimeException exception) {
                log.warn("待受理申请补偿处理失败，等待下一轮补偿，claimApplicationNo={}",
                        claimApplication.getClaimApplicationNo(), exception);
            }
        }
        if (!claimApplications.isEmpty()) {
            log.info("待受理滞留补偿执行完成，compensatedCount={}", claimApplications.size());
        }
        return claimApplications.size();
    }

    /**
     * 对已受理滞留的申请重试承保分流，单条失败不影响同批其他申请。
     *
     * @return 尝试补偿的申请条数
     */    public int redispatchAcceptedClaimApplications() {
        LocalDateTime updatedBefore = now().minus(compensationPolicy.acceptedStallTimeout());
        List<ClaimApplication> claimApplications = claimApplicationRepository.findByStatusUpdatedBefore(ClaimApplicationStatus.ACCEPTED,
                updatedBefore, compensationPolicy.batchSize());
        for (ClaimApplication claimApplication : claimApplications) {
            try {
                underwritingUseCase.dispatch(claimApplication.getClaimApplicationNo(), UUID.randomUUID().toString());
            } catch (RuntimeException exception) {
                log.warn("已受理申请补偿分流失败，等待下一轮补偿，claimApplicationNo={}",
                        claimApplication.getClaimApplicationNo(), exception);
            }
        }
        if (!claimApplications.isEmpty()) {
            log.info("已受理滞留补偿执行完成，compensatedCount={}", claimApplications.size());
        }
        return claimApplications.size();
    }

    /**
     * 对承保中滞留的申请查询承保结果：已完成则推进等待审核，查不到记录则重发自动承保请求。
     *
     * @return 尝试补偿的申请条数
     */
    public int recoverUnderwritingClaimApplications() {
        LocalDateTime updatedBefore = now().minus(compensationPolicy.underwritingTimeout());
        List<ClaimApplication> claimApplications = claimApplicationRepository.findByStatusUpdatedBefore(ClaimApplicationStatus.UNDERWRITING,
                updatedBefore, compensationPolicy.batchSize());
        for (ClaimApplication claimApplication : claimApplications) {
            recoverUnderwritingClaimApplication(claimApplication);
        }
        if (!claimApplications.isEmpty()) {
            log.info("承保中滞留补偿执行完成，compensatedCount={}", claimApplications.size());
        }
        return claimApplications.size();
    }

    /**
     * 对等待审核滞留的申请查询签收状态：已签收则推进完成，未找到则重新交付审核系统。
     *
     * <p>查询失败、已签收但缺少案件号或签收处理失败时只记录轨迹并保持等待审核，
     * 单条异常不中断同批其他申请。</p>
     *
     * @return 尝试补偿的申请条数
     */
    public int recoverWaitAuditClaimApplications() {
        LocalDateTime updatedBefore = now().minus(compensationPolicy.waitAuditTimeout());
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
            auditSignedStatus = auditSignedStatusQuery.query(claimApplicationNo);
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
        String requestId = COMPENSATION_REQUEST_ID_PREFIX + claimApplicationNo;
        try {
            serviceAuditSystemPort.deliver(new AuditDeliveryRequest(claimApplicationNo,
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
                COMPENSATION_REQUEST_ID_PREFIX + claimApplication.getClaimApplicationNo(), InteractionAction.FAILED,
                traceId, errorCode, errorMessage);
    }

    private void recoverUnderwritingClaimApplication(ClaimApplication claimApplication) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        String traceId = UUID.randomUUID().toString();
        String requestId = COMPENSATION_REQUEST_ID_PREFIX + claimApplicationNo;
        String interactionNo = recordFactory.newInteractionNo();
        UnderwritingResult underwritingResult;
        try {
            underwritingResult = underwritingResultQuery.query(claimApplicationNo);
        } catch (RuntimeException exception) {
            log.warn("承保结果查询失败，等待下一轮补偿，claimApplicationNo={}", claimApplicationNo, exception);
            compensationTransactionService.recordAttempt(claimApplicationNo, InteractionType.UNDERWRITING,
                    interactionNo, requestId, InteractionAction.FAILED, traceId, UNDERWRITING_RESULT_QUERY_FAILED_CODE,
                    exception.getMessage());
            return;
        }
        compensationTransactionService.recordAttempt(claimApplicationNo, InteractionType.UNDERWRITING,
                interactionNo, requestId, InteractionAction.COMPLETED, traceId, null, null);
        switch (underwritingResult) {
            case COMPLETED -> completeUnderwriting(claimApplication, traceId);
            case NOT_FOUND -> reRequestAutoUnderwriting(claimApplication, traceId);
            case PROCESSING -> log.info("承保结果仍在处理中，保持承保中状态，claimApplicationNo={}", claimApplicationNo);
        }
    }

    /**
     * 通知丢失时以补偿请求标识推进状态，幂等规则与承保回调一致。
     */
    private void completeUnderwriting(ClaimApplication claimApplication, String traceId) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        try {
            underwritingCallbackUseCase.complete(new UnderwritingCompletedCommand(claimApplicationNo,
                    COMPENSATION_REQUEST_ID_PREFIX + claimApplicationNo, traceId));
        } catch (RuntimeException exception) {
            log.warn("承保完成补偿推进失败，等待下一轮补偿，claimApplicationNo={}", claimApplicationNo, exception);
        }
    }

    /**
     * 承保系统无记录时重发自动承保请求，申请保持承保中状态。
     */
    private void reRequestAutoUnderwriting(ClaimApplication claimApplication, String traceId) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        String interactionNo = recordFactory.newInteractionNo();
        String requestId = COMPENSATION_REQUEST_ID_PREFIX + claimApplicationNo;
        try {
            autoUnderwritingRequester.request(claimApplicationNo, claimApplication.getTenantCode(),
                    claimApplication.primaryInsuredCertificateNo());
            compensationTransactionService.recordAttempt(claimApplicationNo, InteractionType.UNDERWRITING,
                    interactionNo, requestId, InteractionAction.COMPLETED, traceId, null, null);
        } catch (RuntimeException exception) {
            log.warn("自动承保请求补偿重发失败，保持承保中状态，claimApplicationNo={}", claimApplicationNo, exception);
            compensationTransactionService.recordAttempt(claimApplicationNo, InteractionType.UNDERWRITING,
                    interactionNo, requestId, InteractionAction.FAILED, traceId, AUTO_UNDERWRITING_REQUEST_FAILED_CODE,
                    exception.getMessage());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE_ID);
    }

    private String compensationEventId(ClaimApplication claimApplication) {
        return COMPENSATION_REQUEST_ID_PREFIX + claimApplication.getClaimApplicationNo() + "-"
                + claimApplication.getClaimApplicationAttempt();
    }
}
