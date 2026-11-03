package com.wangguangwu.claim.application.application.process.compensation;

import com.wangguangwu.claim.application.application.command.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.application.port.ClaimApplicationCompensationPolicy;
import com.wangguangwu.claim.application.application.port.UnderwritingSystemPort;
import com.wangguangwu.claim.application.application.port.model.InteractionAction;
import com.wangguangwu.claim.application.application.port.model.InteractionType;
import com.wangguangwu.claim.application.application.port.model.UnderwritingResult;
import com.wangguangwu.claim.application.application.process.ClaimApplicationCompensationTransactionService;
import com.wangguangwu.claim.application.application.process.ClaimApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.application.process.UnderwritingCallbackUseCase;
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
 * 承保中滞留补偿：查询承保结果并按结果推进或重发自动承保请求。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class UnderwritingCompensationHandler {

    private static final String UNDERWRITING_RESULT_QUERY_FAILED_CODE = "UNDERWRITING_RESULT_QUERY_FAILED";
    private static final String AUTO_UNDERWRITING_REQUEST_FAILED_CODE = "AUTO_UNDERWRITING_REQUEST_FAILED";

    private final ClaimApplicationRepository claimApplicationRepository;
    private final UnderwritingSystemPort underwritingSystemPort;
    private final UnderwritingCallbackUseCase underwritingCallbackUseCase;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;
    private final ClaimApplicationCompensationTransactionService compensationTransactionService;
    private final ClaimApplicationCompensationPolicy compensationPolicy;

    /**
     * 对承保中滞留的申请查询承保结果：已完成则推进等待审核，查不到记录则重发自动承保请求。
     *
     * @return 尝试补偿的申请条数
     */
    public int recover() {
        LocalDateTime updatedBefore = CompensationDefaults.now().minus(compensationPolicy.underwritingTimeout());
        List<ClaimApplication> claimApplications = claimApplicationRepository.findByStatusUpdatedBefore(
                ClaimApplicationStatus.UNDERWRITING, updatedBefore, compensationPolicy.batchSize());
        for (ClaimApplication claimApplication : claimApplications) {
            recoverUnderwritingClaimApplication(claimApplication);
        }
        if (!claimApplications.isEmpty()) {
            log.info("承保中滞留补偿执行完成，compensatedCount={}", claimApplications.size());
        }
        return claimApplications.size();
    }

    private void recoverUnderwritingClaimApplication(ClaimApplication claimApplication) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        String traceId = UUID.randomUUID().toString();
        String requestId = CompensationDefaults.requestId(claimApplicationNo);
        String interactionNo = recordFactory.newInteractionNo();
        UnderwritingResult underwritingResult;
        try {
            underwritingResult = underwritingSystemPort.queryUnderwritingResult(claimApplicationNo);
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
                    CompensationDefaults.requestId(claimApplicationNo), traceId));
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
        String requestId = CompensationDefaults.requestId(claimApplicationNo);
        try {
            underwritingSystemPort.requestAutoUnderwriting(claimApplicationNo, claimApplication.getTenantCode(),
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
}
