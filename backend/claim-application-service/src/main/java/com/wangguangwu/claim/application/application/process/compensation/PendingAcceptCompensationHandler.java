package com.wangguangwu.claim.application.application.process.compensation;

import com.wangguangwu.claim.application.application.command.MaterialProcessCommand;
import com.wangguangwu.claim.application.application.port.ClaimApplicationCompensationPolicy;
import com.wangguangwu.claim.application.application.process.MaterialProcessingUseCase;
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
 * 待受理滞留补偿：重放停留过久的材料处理。
 *
 * <p>事件标识由申请编号与尝试次数组成，失败回滚后下一轮沿用同一标识重试；
 * 与真实消息并发时由条件更新与消费登记保证只有一次生效。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PendingAcceptCompensationHandler {

    private final ClaimApplicationRepository claimApplicationRepository;
    private final MaterialProcessingUseCase materialProcessingUseCase;
    private final ClaimApplicationCompensationPolicy compensationPolicy;

    /**
     * 对待受理滞留的申请重放材料处理，单条失败不影响同批其他申请。
     *
     * @return 尝试补偿的申请条数
     */
    public int redrive() {
        LocalDateTime updatedBefore = CompensationDefaults.now().minus(compensationPolicy.pendingAcceptTimeout());
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

    private String compensationEventId(ClaimApplication claimApplication) {
        return CompensationDefaults.requestId(claimApplication.getClaimApplicationNo()) + "-"
                + claimApplication.getClaimApplicationAttempt();
    }
}
