package com.wangguangwu.claim.application.infrastructure.scheduling;

import com.wangguangwu.claim.application.usecase.ApplicationCompensationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时触发异步链路补偿，单类补偿异常不中断其余补偿。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class ApplicationCompensationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationCompensationScheduler.class);

    private final ApplicationCompensationUseCase applicationCompensationUseCase;

    public ApplicationCompensationScheduler(ApplicationCompensationUseCase applicationCompensationUseCase) {
        this.applicationCompensationUseCase = applicationCompensationUseCase;
    }

    /**
     * 每轮依次恢复投递滞留、已受理滞留与承保中滞留。
     */
    @Scheduled(fixedDelayString = "${claim.compensation.fixed-delay-ms}")
    public void compensate() {
        runStep("投递滞留重置", applicationCompensationUseCase::resetStuckSendingEvents);
        runStep("已受理滞留补偿", applicationCompensationUseCase::redispatchAcceptedApplications);
        runStep("承保中滞留补偿", applicationCompensationUseCase::recoverUnderwritingApplications);
    }

    private void runStep(String stepName, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException exception) {
            LOGGER.error("补偿任务执行异常，等待下一轮调度重试，stepName={}", stepName, exception);
        }
    }
}
