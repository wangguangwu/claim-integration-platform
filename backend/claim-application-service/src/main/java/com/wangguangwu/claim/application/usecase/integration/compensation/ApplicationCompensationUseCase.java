package com.wangguangwu.claim.application.usecase.integration.compensation;

import com.wangguangwu.claim.application.domain.claim.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.integration.compensation.ApplicationCompensationPolicy;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEventRepository;
import com.wangguangwu.claim.application.domain.underwriting.AutoUnderwritingRequester;
import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResult;
import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResultQuery;
import com.wangguangwu.claim.application.shared.contract.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
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
 * 补偿用例：恢复投递滞留、已受理滞留与承保中滞留，仅在状态仍符合预期时推进。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ApplicationCompensationUseCase {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final String UNDERWRITING_RESULT_QUERY_FAILED_CODE = "UNDERWRITING_RESULT_QUERY_FAILED";
    private static final String AUTO_UNDERWRITING_REQUEST_FAILED_CODE = "AUTO_UNDERWRITING_REQUEST_FAILED";
    private static final String COMPENSATION_REQUEST_ID_PREFIX = "compensation-";

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationRepository applicationRepository;
    private final UnderwritingUseCase underwritingUseCase;
    private final UnderwritingCallbackUseCase underwritingCallbackUseCase;
    private final UnderwritingResultQuery underwritingResultQuery;
    private final AutoUnderwritingRequester autoUnderwritingRequester;
    private final ApplicationLifecycleRecordFactory recordFactory;
    private final InteractionLogRepository interactionLogRepository;
    private final ApplicationCompensationPolicy compensationPolicy;

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
     * 对已受理滞留的申请重试承保分流，单条失败不影响同批其他申请。
     *
     * @return 尝试补偿的申请条数
     */
    public int redispatchAcceptedApplications() {
        LocalDateTime updatedBefore = now().minus(compensationPolicy.acceptedStallTimeout());
        List<ClaimApplication> applications = applicationRepository.findByStatusUpdatedBefore(ApplicationStatus.ACCEPTED,
                updatedBefore, compensationPolicy.batchSize());
        for (ClaimApplication application : applications) {
            try {
                underwritingUseCase.dispatch(application.getApplicationNo(), UUID.randomUUID().toString());
            } catch (RuntimeException exception) {
                log.warn("已受理申请补偿分流失败，等待下一轮补偿，applicationNo={}",
                        application.getApplicationNo(), exception);
            }
        }
        if (!applications.isEmpty()) {
            log.info("已受理滞留补偿执行完成，compensatedCount={}", applications.size());
        }
        return applications.size();
    }

    /**
     * 对承保中滞留的申请查询承保结果：已完成则推进等待审核，查不到记录则重发自动承保请求。
     *
     * @return 尝试补偿的申请条数
     */
    public int recoverUnderwritingApplications() {
        LocalDateTime updatedBefore = now().minus(compensationPolicy.underwritingTimeout());
        List<ClaimApplication> applications = applicationRepository.findByStatusUpdatedBefore(ApplicationStatus.UNDERWRITING,
                updatedBefore, compensationPolicy.batchSize());
        for (ClaimApplication application : applications) {
            recoverUnderwritingApplication(application);
        }
        if (!applications.isEmpty()) {
            log.info("承保中滞留补偿执行完成，compensatedCount={}", applications.size());
        }
        return applications.size();
    }

    private void recoverUnderwritingApplication(ClaimApplication application) {
        String applicationNo = application.getApplicationNo();
        String traceId = UUID.randomUUID().toString();
        String interactionNo = recordFactory.newInteractionNo();
        recordInteraction(applicationNo, interactionNo, InteractionAction.STARTED, traceId, null, null);
        UnderwritingResult underwritingResult;
        try {
            underwritingResult = underwritingResultQuery.query(applicationNo);
        } catch (RuntimeException exception) {
            log.warn("承保结果查询失败，等待下一轮补偿，applicationNo={}", applicationNo, exception);
            recordInteraction(applicationNo, interactionNo, InteractionAction.FAILED, traceId,
                    UNDERWRITING_RESULT_QUERY_FAILED_CODE, exception.getMessage());
            return;
        }
        recordInteraction(applicationNo, interactionNo, InteractionAction.COMPLETED, traceId, null, null);
        switch (underwritingResult) {
            case COMPLETED -> completeUnderwriting(application, traceId);
            case NOT_FOUND -> reRequestAutoUnderwriting(application, traceId);
            case PROCESSING -> log.info("承保结果仍在处理中，保持承保中状态，applicationNo={}", applicationNo);
        }
    }

    /**
     * 通知丢失时以补偿请求标识推进状态，幂等规则与承保回调一致。
     */
    private void completeUnderwriting(ClaimApplication application, String traceId) {
        String applicationNo = application.getApplicationNo();
        try {
            underwritingCallbackUseCase.complete(new UnderwritingCompletedCommand(applicationNo,
                    COMPENSATION_REQUEST_ID_PREFIX + applicationNo, traceId));
        } catch (RuntimeException exception) {
            log.warn("承保完成补偿推进失败，等待下一轮补偿，applicationNo={}", applicationNo, exception);
        }
    }

    /**
     * 承保系统无记录时重发自动承保请求，申请保持承保中状态。
     */
    private void reRequestAutoUnderwriting(ClaimApplication application, String traceId) {
        String applicationNo = application.getApplicationNo();
        String interactionNo = recordFactory.newInteractionNo();
        recordInteraction(applicationNo, interactionNo, InteractionAction.STARTED, traceId, null, null);
        try {
            autoUnderwritingRequester.request(applicationNo, application.getTenantCode(),
                    application.primaryInsuredCertificateNo());
            recordInteraction(applicationNo, interactionNo, InteractionAction.COMPLETED, traceId, null, null);
        } catch (RuntimeException exception) {
            log.warn("自动承保请求补偿重发失败，保持承保中状态，applicationNo={}", applicationNo, exception);
            recordInteraction(applicationNo, interactionNo, InteractionAction.FAILED, traceId,
                    AUTO_UNDERWRITING_REQUEST_FAILED_CODE, exception.getMessage());
        }
    }

    private void recordInteraction(String applicationNo, String interactionNo, InteractionAction action, String traceId,
                                   String errorCode, String errorMessage) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.UNDERWRITING, applicationNo,
                interactionNo, action, COMPENSATION_REQUEST_ID_PREFIX + applicationNo, traceId, errorCode,
                errorMessage));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE_ID);
    }
}
