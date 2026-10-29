package com.wangguangwu.claim.application.interfaces.integration.scheduling;

import com.wangguangwu.claim.application.usecase.integration.compensation.ClaimApplicationCompensationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时触发异步链路补偿，单类补偿异常不中断其余补偿。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ClaimApplicationCompensationScheduler {

    private final ClaimApplicationCompensationUseCase claimApplicationCompensationUseCase;

    /**
     * 每轮依次恢复投递滞留、已受理滞留与承保中滞留。
     */
    @Scheduled(fixedDelayString = "${claim.compensation.fixed-delay-ms}")
    public void compensate() {
        runStep("投递滞留重置", claimApplicationCompensationUseCase::resetStuckSendingEvents);
        runStep("已受理滞留补偿", claimApplicationCompensationUseCase::redispatchAcceptedClaimApplications);
        runStep("承保中滞留补偿", claimApplicationCompensationUseCase::recoverUnderwritingClaimApplications);
    }

    private void runStep(String stepName, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException exception) {
            log.error("补偿任务执行异常，等待下一轮调度重试，stepName={}", stepName, exception);
        }
    }
}
