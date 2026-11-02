package com.wangguangwu.claim.application.usecase.audit;

import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.usecase.integration.consumption.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.shared.contract.ClaimApplicationAsyncErrorCode;
import com.wangguangwu.claim.application.usecase.audit.command.AuditSignedCommand;
import com.wangguangwu.claim.application.usecase.audit.result.AuditSignedOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审核签收用例：消费登记预判、申请存在性校验，并委托事务服务写库。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AuditSignedUseCase {

    private final ClaimApplicationRepository claimApplicationRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final AuditSignedTransactionService transactionService;

    /**
     * 处理一次审核签收事件。
     *
     * @param command 审核签收命令
     * @return 处理结果
     */
    public AuditSignedOutcome record(AuditSignedCommand command) {
        if (consumedEventRepository.exists(AuditSignedTransactionService.CONSUMER_GROUP, command.eventId())) {
            log.info("审核签收事件已消费，跳过处理，claimApplicationNo={}，eventId={}", command.claimApplicationNo(),
                    command.eventId());
            return AuditSignedOutcome.SKIPPED;
        }
        claimApplicationRepository.findByClaimApplicationNo(command.claimApplicationNo())
                .orElseThrow(() -> new BusinessException(
                        ClaimApplicationAsyncErrorCode.CLAIM_APPLICATION_NOT_FOUND,
                        "审核签收事件对应的申请不存在，claimApplicationNo=" + command.claimApplicationNo()
                                + "，eventId=" + command.eventId()));
        AuditSignedOutcome outcome = transactionService.record(command);
        log.info("审核签收处理完成，claimApplicationNo={}，eventId={}，caseNoCount={}，outcome={}",
                command.claimApplicationNo(), command.eventId(), command.caseNos().size(), outcome);
        return outcome;
    }
}
