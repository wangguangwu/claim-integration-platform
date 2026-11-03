package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.application.command.AuditSignedCommand;
import com.wangguangwu.claim.application.application.port.ConsumedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审核签收用例：只做消费登记预判，业务校验与状态迁移全部下沉到事务服务。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AuditSignedUseCase {

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
        AuditSignedOutcome outcome = transactionService.record(command);
        log.info("审核签收处理完成，claimApplicationNo={}，eventId={}，caseNoCount={}，outcome={}",
                command.claimApplicationNo(), command.eventId(), command.caseNos().size(), outcome);
        return outcome;
    }
}
