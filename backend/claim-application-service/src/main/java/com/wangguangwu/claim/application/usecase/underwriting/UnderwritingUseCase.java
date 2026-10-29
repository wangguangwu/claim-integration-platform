package com.wangguangwu.claim.application.usecase.underwriting;

import com.wangguangwu.claim.application.domain.claim.port.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.underwriting.port.AutoUnderwritingRequester;
import com.wangguangwu.claim.application.domain.underwriting.PolicySnapshot;
import com.wangguangwu.claim.application.domain.underwriting.port.ValidPolicyQuery;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 承保分流用例：事务外查询有效保单，按结果推进到等待审核签收或自动承保。
 *
 * <p>保单查询与自动承保发起都在事务外执行，外部调用失败时保持申请原状态，
 * 由补偿任务按 `ACCEPTED` 或 `UNDERWRITING` 滞留重试。两次调用的开始与结束轨迹都由
 * 事务服务一次写入，调用中断时不会留下孤儿轨迹。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UnderwritingUseCase {

    private static final String POLICY_QUERY_FAILED_CODE = "POLICY_QUERY_FAILED";
    private static final String AUTO_UNDERWRITING_REQUEST_FAILED_CODE = "AUTO_UNDERWRITING_REQUEST_FAILED";

    private final ApplicationRepository applicationRepository;
    private final ValidPolicyQuery validPolicyQuery;
    private final AutoUnderwritingRequester autoUnderwritingRequester;
    private final UnderwritingTransactionService transactionService;
    private final ApplicationLifecycleRecordFactory recordFactory;

    /**
     * 对处于已受理状态的申请执行承保分流。
     *
     * @param applicationNo 平台申请编号
     * @param traceId       链路标识
     */
    public void dispatch(String applicationNo, String traceId) {
        ClaimApplication application = applicationRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new IllegalStateException("承保分流对应的申请不存在，applicationNo=" + applicationNo));
        if (!application.isInStatus(ApplicationStatus.ACCEPTED)) {
            log.info("申请不处于已受理状态，跳过承保分流，applicationNo={}，status={}", applicationNo,
                    application.getStatus());
            return;
        }
        String insuredCertificateNo = application.primaryInsuredCertificateNo();
        Optional<PolicyQueryOutcome> queryOutcome = queryValidPolicy(application, insuredCertificateNo, traceId);
        if (queryOutcome.isEmpty()) {
            return;
        }
        Optional<PolicySnapshot> policySnapshot = queryOutcome.get().policySnapshot();
        if (policySnapshot.isPresent()) {
            if (!transactionService.promoteToWaitAudit(application, traceId)) {
                log.info("承保分流状态已被其他执行者推进，applicationNo={}", applicationNo);
            }
            return;
        }
        if (!transactionService.startUnderwriting(application)) {
            log.info("自动承保状态已被其他执行者推进，applicationNo={}", applicationNo);
            return;
        }
        requestAutoUnderwriting(application, insuredCertificateNo, traceId);
    }

    /**
     * 查询有效保单并记录行为组轨迹。
     *
     * @return 查询结果；查询失败时为空，调用方保持申请原状态
     */
    private Optional<PolicyQueryOutcome> queryValidPolicy(ClaimApplication application, String insuredCertificateNo,
                                                          String traceId) {
        String applicationNo = application.getApplicationNo();
        String interactionNo = recordFactory.newInteractionNo();
        try {
            Optional<PolicySnapshot> policySnapshot = validPolicyQuery.findValidPolicy(application.getTenantCode(),
                    insuredCertificateNo);
            transactionService.recordAttempt(applicationNo, interactionNo, InteractionAction.COMPLETED, traceId,
                    null, null);
            return Optional.of(new PolicyQueryOutcome(policySnapshot));
        } catch (RuntimeException exception) {
            log.warn("有效保单查询失败，申请保持已受理状态等待补偿，applicationNo={}", applicationNo, exception);
            transactionService.recordAttempt(applicationNo, interactionNo, InteractionAction.FAILED, traceId,
                    POLICY_QUERY_FAILED_CODE, exception.getMessage());
            return Optional.empty();
        }
    }

    private void requestAutoUnderwriting(ClaimApplication application, String insuredCertificateNo, String traceId) {
        String applicationNo = application.getApplicationNo();
        String interactionNo = recordFactory.newInteractionNo();
        try {
            autoUnderwritingRequester.request(applicationNo, application.getTenantCode(), insuredCertificateNo);
            transactionService.recordAttempt(applicationNo, interactionNo, InteractionAction.COMPLETED, traceId,
                    null, null);
        } catch (RuntimeException exception) {
            log.warn("自动承保请求失败，申请保持承保中状态等待补偿重发，applicationNo={}", applicationNo, exception);
            transactionService.recordAttempt(applicationNo, interactionNo, InteractionAction.FAILED, traceId,
                    AUTO_UNDERWRITING_REQUEST_FAILED_CODE, exception.getMessage());
        }
    }

    /**
     * 一次保单查询的结果，区分"查询完成但无有效保单"与"查询失败"。
     *
     * @param policySnapshot 有效保单
     */
    private record PolicyQueryOutcome(Optional<PolicySnapshot> policySnapshot) {
    }
}
