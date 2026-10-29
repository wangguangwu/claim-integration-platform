package com.wangguangwu.claim.application.usecase.material;

import com.wangguangwu.claim.application.domain.claim.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.integration.consumption.ConsumedEventRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.material.Material;
import com.wangguangwu.claim.application.domain.material.MaterialRepository;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.material.command.MaterialProcessCommand;
import com.wangguangwu.claim.application.usecase.material.result.MaterialProcessOutcome;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 材料处理的数据库事务服务，保证材料结果、状态、轨迹与消费登记同时生效。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MaterialProcessingTransactionService {

    /**
     * 材料处理消费者组，与 002 冻结的绑定配置一致。
     */
    public static final String CONSUMER_GROUP = "claim-application-material-processor-v1";

    private final ConsumedEventRepository consumedEventRepository;
    private final ApplicationRepository applicationRepository;
    private final MaterialRepository materialRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final ApplicationLifecycleRecordFactory recordFactory;

    /**
     * 记录状态或尝试次数不匹配的复用轨迹并登记消费。
     *
     * @param command       材料处理命令
     * @param interactionNo 行为组编号
     * @return 处理结果
     */
    @Transactional
    public MaterialProcessOutcome recordReused(MaterialProcessCommand command, String interactionNo) {
        if (!consumedEventRepository.register(CONSUMER_GROUP, command.eventId(), command.applicationNo())) {
            return MaterialProcessOutcome.SKIPPED;
        }
        recordFinished(command, interactionNo, InteractionAction.REUSED, null, null);
        return MaterialProcessOutcome.REUSED;
    }

    /**
     * 写入材料处理结果、申请状态、结束轨迹与消费登记。
     *
     * <p>材料结果使用条件更新，重复消息不会覆盖已完成结果；申请状态使用带尝试次数的条件更新，
     * 影响行数为 0 说明已被其他执行者推进，按重复处理并记录复用轨迹。</p>
     *
     * @param command       材料处理命令
     * @param interactionNo 行为组编号
     * @param application   已读取的申请
     * @param materials     已写入处理结果的材料
     * @param allSucceeded  是否全部处理成功
     * @param errorCode     失败错误码，全部成功时为 null
     * @param errorMessage  失败说明，全部成功时为 null
     * @return 处理结果
     */
    @Transactional
    public MaterialProcessOutcome recordProcessed(MaterialProcessCommand command, String interactionNo,
                                                  ClaimApplication application, List<Material> materials,
                                                  boolean allSucceeded, String errorCode, String errorMessage) {
        if (!consumedEventRepository.register(CONSUMER_GROUP, command.eventId(), command.applicationNo())) {
            return MaterialProcessOutcome.SKIPPED;
        }
        for (Material material : materials) {
            if (!materialRepository.updateProcessResult(material)) {
                log.warn("材料处理结果未写入，材料已不是待处理状态，applicationNo={}，materialType={}",
                        material.getApplicationNo(), material.getMaterialType());
            }
        }
        if (allSucceeded) {
            application.accept();
        } else {
            application.failAccept();
        }
        if (!applicationRepository.updateStatus(application, ApplicationStatus.PENDING_ACCEPT)) {
            recordFinished(command, interactionNo, InteractionAction.REUSED, null, null);
            return MaterialProcessOutcome.REUSED;
        }
        recordFinished(command, interactionNo, allSucceeded ? InteractionAction.COMPLETED : InteractionAction.FAILED,
                errorCode, errorMessage);
        return allSucceeded ? MaterialProcessOutcome.ACCEPTED : MaterialProcessOutcome.ACCEPT_FAILED;
    }

    private void recordFinished(MaterialProcessCommand command, String interactionNo, InteractionAction action,
                                String errorCode, String errorMessage) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.MATERIAL_PROCESS,
                command.applicationNo(), interactionNo, action, command.eventId(), command.traceId(), errorCode,
                errorMessage));
    }
}
