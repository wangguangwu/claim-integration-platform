package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.application.command.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.application.port.model.InteractionAction;
import com.wangguangwu.claim.application.application.port.model.UnderwritingResult;
import com.wangguangwu.claim.application.application.port.UnderwritingSystemPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 承保分流用例：事务外查询有效保单，按结果推进到等待审核签收或自动承保。
 *
 * <p>保单查询与自动承保发起都在事务外执行，外部调用失败时保持申请原状态，
 * 由补偿任务按 `ACCEPTED` 或 `UNDERWRITING` 滞留重试。自动承保发起后立即查询一次结果，
 * 承保系统同步完成时直接推进等待审核，仍在处理时由补偿任务继续查询。两次调用的开始与结束轨迹
 * 都由事务服务一次写入，调用中断时不会留下孤儿轨迹。</p>
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

    private final ClaimApplicationRepository claimApplicationRepository;
    private final UnderwritingSystemPort underwritingSystemPort;
    private final UnderwritingTransactionService transactionService;
    private final UnderwritingCallbackUseCase underwritingCallbackUseCase;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;

    /**
     * 对处于已受理状态的申请执行承保分流。
     *
     * @param claimApplicationNo 平台申请编号
     * @param traceId       链路标识
     */
    public void dispatch(String claimApplicationNo, String traceId) {
        ClaimApplication claimApplication = claimApplicationRepository.findByClaimApplicationNo(claimApplicationNo)
                .orElseThrow(() -> new IllegalStateException("承保分流对应的申请不存在，claimApplicationNo=" + claimApplicationNo));
        if (!claimApplication.isAccepted()) {
            log.info("申请不处于已受理状态，跳过承保分流，claimApplicationNo={}，status={}", claimApplicationNo,
                    claimApplication.getStatus());
            return;
        }
        String insuredCertificateNo = claimApplication.primaryInsuredCertificateNo();
        Optional<PolicyQueryOutcome> queryOutcome = queryValidPolicy(claimApplication, insuredCertificateNo, traceId);
        if (queryOutcome.isEmpty()) {
            return;
        }
        if (queryOutcome.get().hasValidPolicy()) {
            if (!transactionService.promoteToWaitAudit(claimApplication, traceId)) {
                log.info("承保分流状态已被其他执行者推进，claimApplicationNo={}", claimApplicationNo);
            }
            return;
        }
        if (!transactionService.startUnderwriting(claimApplication)) {
            log.info("自动承保状态已被其他执行者推进，claimApplicationNo={}", claimApplicationNo);
            return;
        }
        requestAutoUnderwriting(claimApplication, insuredCertificateNo, traceId);
    }

    /**
     * 查询有效保单存在性并记录行为组轨迹。
     *
     * @return 查询结果；查询失败时为空，调用方保持申请原状态
     */
    private Optional<PolicyQueryOutcome> queryValidPolicy(ClaimApplication claimApplication, String insuredCertificateNo,
                                                          String traceId) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        String interactionNo = recordFactory.newInteractionNo();
        try {
            boolean hasValidPolicy = underwritingSystemPort.hasValidPolicy(claimApplication.getTenantCode(),
                    insuredCertificateNo);
            transactionService.recordAttempt(claimApplicationNo, interactionNo, InteractionAction.COMPLETED, traceId,
                    null, null);
            return Optional.of(new PolicyQueryOutcome(hasValidPolicy));
        } catch (RuntimeException exception) {
            log.warn("有效保单查询失败，申请保持已受理状态等待补偿，claimApplicationNo={}", claimApplicationNo, exception);
            transactionService.recordAttempt(claimApplicationNo, interactionNo, InteractionAction.FAILED, traceId,
                    POLICY_QUERY_FAILED_CODE, exception.getMessage());
            return Optional.empty();
        }
    }

    private void requestAutoUnderwriting(ClaimApplication claimApplication, String insuredCertificateNo, String traceId) {
        String claimApplicationNo = claimApplication.getClaimApplicationNo();
        String interactionNo = recordFactory.newInteractionNo();
        try {
            underwritingSystemPort.requestAutoUnderwriting(claimApplicationNo, claimApplication.getTenantCode(),
                    insuredCertificateNo);
            transactionService.recordAttempt(claimApplicationNo, interactionNo, InteractionAction.COMPLETED, traceId,
                    null, null);
            completeUnderwritingIfFinished(claimApplicationNo, traceId);
        } catch (RuntimeException exception) {
            log.warn("自动承保请求失败，申请保持承保中状态等待补偿重发，claimApplicationNo={}", claimApplicationNo, exception);
            transactionService.recordAttempt(claimApplicationNo, interactionNo, InteractionAction.FAILED, traceId,
                    AUTO_UNDERWRITING_REQUEST_FAILED_CODE, exception.getMessage());
        }
    }

    /**
     * 承保系统同步返回完成时立即推进等待审核，仍在处理或查询失败时保持承保中等待补偿。
     */
    private void completeUnderwritingIfFinished(String claimApplicationNo, String traceId) {
        UnderwritingResult underwritingResult;
        try {
            underwritingResult = underwritingSystemPort.queryUnderwritingResult(claimApplicationNo);
        } catch (RuntimeException exception) {
            log.warn("自动承保结果查询失败，申请保持承保中状态等待补偿，claimApplicationNo={}", claimApplicationNo,
                    exception);
            return;
        }
        if (underwritingResult != UnderwritingResult.COMPLETED) {
            log.info("自动承保仍在处理中，申请保持承保中状态，claimApplicationNo={}，result={}", claimApplicationNo,
                    underwritingResult);
            return;
        }
        try {
            underwritingCallbackUseCase.complete(new UnderwritingCompletedCommand(claimApplicationNo,
                    UUID.randomUUID().toString(), traceId));
        } catch (RuntimeException exception) {
            log.warn("承保完成推进失败，申请保持承保中状态等待补偿，claimApplicationNo={}", claimApplicationNo, exception);
        }
    }

    /**
     * 一次保单查询的结果，区分"查询完成"与"查询失败"，失败由调用方保持原状态。
     *
     * @param hasValidPolicy 被保人是否存在有效保单
     */
    private record PolicyQueryOutcome(boolean hasValidPolicy) {
    }
}
