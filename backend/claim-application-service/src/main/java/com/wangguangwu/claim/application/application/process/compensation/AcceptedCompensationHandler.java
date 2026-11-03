package com.wangguangwu.claim.application.application.process.compensation;

import com.wangguangwu.claim.application.application.port.ClaimApplicationCompensationPolicy;
import com.wangguangwu.claim.application.application.process.UnderwritingUseCase;
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
 * 已受理滞留补偿：对停留过久的已受理申请重试承保分流。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AcceptedCompensationHandler {

    private final ClaimApplicationRepository claimApplicationRepository;
    private final UnderwritingUseCase underwritingUseCase;
    private final ClaimApplicationCompensationPolicy compensationPolicy;

    /**
     * 对已受理滞留的申请重试承保分流，单条失败不影响同批其他申请。
     *
     * @return 尝试补偿的申请条数
     */
    public int redispatch() {
        LocalDateTime updatedBefore = CompensationDefaults.now().minus(compensationPolicy.acceptedStallTimeout());
        List<ClaimApplication> claimApplications = claimApplicationRepository.findByStatusUpdatedBefore(
                ClaimApplicationStatus.ACCEPTED, updatedBefore, compensationPolicy.batchSize());
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
}
