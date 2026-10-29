package com.wangguangwu.claim.application.usecase.audit;

import com.wangguangwu.claim.application.domain.integration.consumption.ConsumedEventRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.usecase.audit.command.AuditDeliveryCommand;
import com.wangguangwu.claim.application.usecase.audit.result.AuditDeliveryOutcome;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审核交付的数据库事务服务，保证行为轨迹与消费登记同时生效。
 *
 * <p>审核交付不改变申请状态，事务内只写交互轨迹与消费登记；
 * 消费登记唯一键冲突说明同一事件已被处理，直接返回跳过。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AuditDeliveryTransactionService {

    /**
     * 审核交付消费者组，与绑定配置及死信主题约定一致。
     */
    public static final String CONSUMER_GROUP = "claim-application-audit-delivery-v1";

    private final ConsumedEventRepository consumedEventRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;

    /**
     * 记录状态不符时的复用轨迹并登记消费。
     *
     * @param command       审核交付命令
     * @param interactionNo 行为组编号
     * @return 处理结果
     */
    @Transactional
    public AuditDeliveryOutcome recordReused(AuditDeliveryCommand command, String interactionNo) {
        if (!consumedEventRepository.register(CONSUMER_GROUP, command.eventId(), command.claimApplicationNo())) {
            return AuditDeliveryOutcome.SKIPPED;
        }
        recordStarted(command, interactionNo);
        recordFinished(command, interactionNo, InteractionAction.REUSED, null, null);
        return AuditDeliveryOutcome.REUSED;
    }

    /**
     * 记录交付成功的完整行为组并登记消费。
     *
     * @param command       审核交付命令
     * @param interactionNo 行为组编号
     * @return 处理结果
     */
    @Transactional
    public AuditDeliveryOutcome recordDelivered(AuditDeliveryCommand command, String interactionNo) {
        if (!consumedEventRepository.register(CONSUMER_GROUP, command.eventId(), command.claimApplicationNo())) {
            return AuditDeliveryOutcome.SKIPPED;
        }
        recordStarted(command, interactionNo);
        recordFinished(command, interactionNo, InteractionAction.COMPLETED, null, null);
        return AuditDeliveryOutcome.DELIVERED;
    }

    private void recordStarted(AuditDeliveryCommand command, String interactionNo) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.AUDIT_DELIVERY,
                command.claimApplicationNo(), interactionNo, InteractionAction.STARTED, command.eventId(),
                command.traceId(), null, null));
    }

    private void recordFinished(AuditDeliveryCommand command, String interactionNo, InteractionAction action,
                                String errorCode, String errorMessage) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.AUDIT_DELIVERY,
                command.claimApplicationNo(), interactionNo, action, command.eventId(), command.traceId(), errorCode,
                errorMessage));
    }
}
