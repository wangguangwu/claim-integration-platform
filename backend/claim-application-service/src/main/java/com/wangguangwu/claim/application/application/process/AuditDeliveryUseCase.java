package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.application.port.model.AuditDeliveryRequest;
import com.wangguangwu.claim.application.application.port.AuditSystemPort;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.application.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.application.exception.ApplicationException;
import com.wangguangwu.claim.application.application.command.AuditDeliveryCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审核交付用例：判重、校验申请状态、调用审核系统并把轨迹与消费登记写入同一事务。
 *
 * <p>外部调用在事务外执行：交付失败时异常向上抛出，事务不启动，消息重投后可重新交付。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AuditDeliveryUseCase {

    private final ClaimApplicationRepository claimApplicationRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final AuditSystemPort auditSystemPort;
    private final AuditDeliveryTransactionService transactionService;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;

    /**
     * 处理一次审核交付事件。
     *
     * @param command 审核交付命令
     */
    public void deliver(AuditDeliveryCommand command) {
        if (consumedEventRepository.exists(AuditDeliveryTransactionService.CONSUMER_GROUP, command.eventId())) {
            log.info("审核交付事件已消费，跳过处理，claimApplicationNo={}，eventId={}", command.claimApplicationNo(),
                    command.eventId());
            return;
        }
        ClaimApplication claimApplication = claimApplicationRepository
                .findByClaimApplicationNo(command.claimApplicationNo())
                .orElseThrow(() -> new ApplicationException(
                        ClaimApplicationIntakeErrorCode.AUDIT_DELIVERY_APPLICATION_NOT_FOUND,
                        "审核交付事件对应的申请不存在，可能 outbox 事件早于申请落库到达，claimApplicationNo="
                                + command.claimApplicationNo() + "，eventId=" + command.eventId()));
        String interactionNo = recordFactory.newInteractionNo();
        if (!claimApplication.isAwaitingAudit()) {
            log.info("审核交付事件与当前申请不匹配，按重复事件处理，claimApplicationNo={}，status={}",
                    command.claimApplicationNo(), claimApplication.getStatus());
            transactionService.recordReused(command, interactionNo);
            return;
        }
        auditSystemPort.deliver(new AuditDeliveryRequest(command.claimApplicationNo(), command.tenantCode(),
                command.traceId()));
        AuditDeliveryOutcome outcome = transactionService.recordDelivered(command, interactionNo);
        log.info("审核交付完成，claimApplicationNo={}，eventId={}，outcome={}", command.claimApplicationNo(),
                command.eventId(), outcome);
    }
}
