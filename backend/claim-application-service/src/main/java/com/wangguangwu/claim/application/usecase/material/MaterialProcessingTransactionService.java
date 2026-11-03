package com.wangguangwu.claim.application.usecase.material;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.material.MaterialProcessingResult;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.usecase.integration.consumption.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionAction;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionType;
import com.wangguangwu.claim.application.usecase.integration.interaction.port.InteractionLogRepository;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.material.command.MaterialProcessCommand;
import com.wangguangwu.claim.application.usecase.material.result.MaterialProcessOutcome;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 材料处理的数据库事务服务，保证材料结果、申请状态、轨迹与消费登记同时生效。
 *
 * <p>事务内重新加载聚合，校验状态与受理尝试次数后才写入结果；聚合保存使用版本条件，
 * 版本过期或前置条件不满足统一按重复事件处理，不覆盖其他执行者已写入的结果。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class MaterialProcessingTransactionService {

    /**
     * 材料处理消费者组，与 002 冻结的绑定配置一致。
     */
    public static final String CONSUMER_GROUP = "claim-application-material-processor-v1";

    private final ConsumedEventRepository consumedEventRepository;
    private final ClaimApplicationRepository claimApplicationRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;

    /**
     * 记录状态或尝试次数不匹配的复用轨迹并登记消费。
     *
     * @param command       材料处理命令
     * @param interactionNo 行为组编号
     * @return 处理结果
     */
    @Transactional
    public MaterialProcessOutcome recordReused(MaterialProcessCommand command, String interactionNo) {
        if (!consumedEventRepository.register(CONSUMER_GROUP, command.eventId(), command.claimApplicationNo())) {
            return MaterialProcessOutcome.SKIPPED;
        }
        recordStarted(command, interactionNo);
        recordFinished(command, interactionNo, InteractionAction.REUSED, null, null);
        return MaterialProcessOutcome.REUSED;
    }

    /**
     * 重新加载聚合、写入材料处理结果并保存。
     *
     * @param command       材料处理命令
     * @param interactionNo 行为组编号
     * @param results       与当前待处理材料一一对应的处理结果
     * @param errorCode     失败错误码，全部成功时为 null
     * @param errorMessage  失败说明，全部成功时为 null
     * @return 处理结果
     */
    @Transactional
    public MaterialProcessOutcome recordProcessed(MaterialProcessCommand command, String interactionNo,
                                                  List<MaterialProcessingResult> results, String errorCode,
                                                  String errorMessage) {
        if (!consumedEventRepository.register(CONSUMER_GROUP, command.eventId(), command.claimApplicationNo())) {
            return MaterialProcessOutcome.SKIPPED;
        }
        ClaimApplication claimApplication = claimApplicationRepository.findByClaimApplicationNo(
                        command.claimApplicationNo())
                .orElseThrow(() -> new IllegalStateException("材料处理事件对应的申请不存在，claimApplicationNo="
                        + command.claimApplicationNo()));
        recordStarted(command, interactionNo);
        if (!claimApplication.isPendingAccept()
                || !Objects.equals(claimApplication.getClaimApplicationAttempt(), command.claimApplicationAttempt())) {
            recordFinished(command, interactionNo, InteractionAction.REUSED, null, null);
            return MaterialProcessOutcome.REUSED;
        }
        claimApplication.applyMaterialResults(results);
        if (!claimApplicationRepository.save(claimApplication)) {
            recordFinished(command, interactionNo, InteractionAction.REUSED, null, null);
            return MaterialProcessOutcome.REUSED;
        }
        boolean allSucceeded = claimApplication.isAccepted();
        recordFinished(command, interactionNo, allSucceeded ? InteractionAction.COMPLETED : InteractionAction.FAILED,
                errorCode, errorMessage);
        return allSucceeded ? MaterialProcessOutcome.ACCEPTED : MaterialProcessOutcome.ACCEPT_FAILED;
    }

    private void recordStarted(MaterialProcessCommand command, String interactionNo) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.MATERIAL_PROCESS,
                command.claimApplicationNo(), interactionNo, InteractionAction.STARTED, command.eventId(),
                command.traceId(), null, null));
    }

    private void recordFinished(MaterialProcessCommand command, String interactionNo, InteractionAction action,
                                String errorCode, String errorMessage) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.MATERIAL_PROCESS,
                command.claimApplicationNo(), interactionNo, action, command.eventId(), command.traceId(), errorCode,
                errorMessage));
    }
}
