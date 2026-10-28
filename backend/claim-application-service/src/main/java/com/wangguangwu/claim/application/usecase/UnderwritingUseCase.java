package com.wangguangwu.claim.application.usecase;

import com.wangguangwu.claim.application.domain.application.Application;
import com.wangguangwu.claim.application.domain.application.ApplicationRepository;
import com.wangguangwu.claim.application.domain.application.ApplicationStatus;
import com.wangguangwu.claim.application.domain.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.underwriting.AutoUnderwritingRequester;
import com.wangguangwu.claim.application.domain.underwriting.PolicySnapshot;
import com.wangguangwu.claim.application.domain.underwriting.ValidPolicyQuery;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 承保分流用例：事务外查询有效保单，按结果推进到等待审核签收或自动承保。
 *
 * <p>保单查询与自动承保发起都在事务外执行，外部调用失败时保持申请原状态，
 * 由补偿任务按 `ACCEPTED` 或 `UNDERWRITING` 滞留重试。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Service
public class UnderwritingUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnderwritingUseCase.class);
    private static final String POLICY_QUERY_FAILED_CODE = "POLICY_QUERY_FAILED";
    private static final String AUTO_UNDERWRITING_REQUEST_FAILED_CODE = "AUTO_UNDERWRITING_REQUEST_FAILED";

    private final ApplicationRepository applicationRepository;
    private final ApplicationPersonReader applicationPersonReader;
    private final ValidPolicyQuery validPolicyQuery;
    private final AutoUnderwritingRequester autoUnderwritingRequester;
    private final UnderwritingTransactionService transactionService;
    private final ApplicationLifecycleRecordFactory recordFactory;
    private final InteractionLogRepository interactionLogRepository;

    public UnderwritingUseCase(ApplicationRepository applicationRepository,
                               ApplicationPersonReader applicationPersonReader,
                               ValidPolicyQuery validPolicyQuery,
                               AutoUnderwritingRequester autoUnderwritingRequester,
                               UnderwritingTransactionService transactionService,
                               ApplicationLifecycleRecordFactory recordFactory,
                               InteractionLogRepository interactionLogRepository) {
        this.applicationRepository = applicationRepository;
        this.applicationPersonReader = applicationPersonReader;
        this.validPolicyQuery = validPolicyQuery;
        this.autoUnderwritingRequester = autoUnderwritingRequester;
        this.transactionService = transactionService;
        this.recordFactory = recordFactory;
        this.interactionLogRepository = interactionLogRepository;
    }

    /**
     * 对处于已受理状态的申请执行承保分流。
     *
     * @param applicationNo 平台申请编号
     * @param traceId       链路标识
     */
    public void dispatch(String applicationNo, String traceId) {
        Application application = applicationRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new IllegalStateException("承保分流对应的申请不存在，applicationNo=" + applicationNo));
        if (!application.isInStatus(ApplicationStatus.ACCEPTED)) {
            LOGGER.info("申请不处于已受理状态，跳过承保分流，applicationNo={}，status={}", applicationNo,
                    application.getStatus());
            return;
        }
        String insuredCertificateNo = applicationPersonReader.readPrimaryInsuredCertificateNo(application);
        Optional<PolicyQueryOutcome> queryOutcome = queryValidPolicy(application, insuredCertificateNo, traceId);
        if (queryOutcome.isEmpty()) {
            return;
        }
        Optional<PolicySnapshot> policySnapshot = queryOutcome.get().policySnapshot();
        if (policySnapshot.isPresent()) {
            if (!transactionService.promoteToWaitAudit(application, traceId)) {
                LOGGER.info("承保分流状态已被其他执行者推进，applicationNo={}", applicationNo);
            }
            return;
        }
        if (!transactionService.startUnderwriting(application)) {
            LOGGER.info("自动承保状态已被其他执行者推进，applicationNo={}", applicationNo);
            return;
        }
        requestAutoUnderwriting(application, insuredCertificateNo, traceId);
    }

    /**
     * 查询有效保单并记录行为组轨迹。
     *
     * @return 查询结果；查询失败时为空，调用方保持申请原状态
     */
    private Optional<PolicyQueryOutcome> queryValidPolicy(Application application, String insuredCertificateNo,
                                                          String traceId) {
        String applicationNo = application.getApplicationNo();
        String interactionNo = recordFactory.newInteractionNo();
        recordInteraction(applicationNo, interactionNo, InteractionAction.STARTED, traceId, null, null);
        try {
            Optional<PolicySnapshot> policySnapshot = validPolicyQuery.findValidPolicy(application.getTenantCode(),
                    insuredCertificateNo);
            recordInteraction(applicationNo, interactionNo, InteractionAction.COMPLETED, traceId, null, null);
            return Optional.of(new PolicyQueryOutcome(policySnapshot));
        } catch (RuntimeException exception) {
            LOGGER.warn("有效保单查询失败，申请保持已受理状态等待补偿，applicationNo={}", applicationNo, exception);
            recordInteraction(applicationNo, interactionNo, InteractionAction.FAILED, traceId,
                    POLICY_QUERY_FAILED_CODE, exception.getMessage());
            return Optional.empty();
        }
    }

    private void requestAutoUnderwriting(Application application, String insuredCertificateNo, String traceId) {
        String applicationNo = application.getApplicationNo();
        String interactionNo = recordFactory.newInteractionNo();
        recordInteraction(applicationNo, interactionNo, InteractionAction.STARTED, traceId, null, null);
        try {
            autoUnderwritingRequester.request(applicationNo, application.getTenantCode(), insuredCertificateNo);
            recordInteraction(applicationNo, interactionNo, InteractionAction.COMPLETED, traceId, null, null);
        } catch (RuntimeException exception) {
            LOGGER.warn("自动承保请求失败，申请保持承保中状态等待补偿重发，applicationNo={}", applicationNo, exception);
            recordInteraction(applicationNo, interactionNo, InteractionAction.FAILED, traceId,
                    AUTO_UNDERWRITING_REQUEST_FAILED_CODE, exception.getMessage());
        }
    }

    private void recordInteraction(String applicationNo, String interactionNo, InteractionAction action, String traceId,
                                   String errorCode, String errorMessage) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.UNDERWRITING, applicationNo,
                interactionNo, action, null, traceId, errorCode, errorMessage));
    }

    /**
     * 一次保单查询的结果，区分"查询完成但无有效保单"与"查询失败"。
     *
     * @param policySnapshot 有效保单
     */
    private record PolicyQueryOutcome(Optional<PolicySnapshot> policySnapshot) {
    }
}
