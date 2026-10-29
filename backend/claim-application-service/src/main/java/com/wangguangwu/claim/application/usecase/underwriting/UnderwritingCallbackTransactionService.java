package com.wangguangwu.claim.application.usecase.underwriting;

import com.wangguangwu.claim.application.domain.claim.port.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.integration.outbox.port.OutboxEventRepository;
import com.wangguangwu.claim.application.shared.contract.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
import com.wangguangwu.claim.application.usecase.underwriting.result.UnderwritingCallbackDisposition;
import com.wangguangwu.claim.application.usecase.underwriting.result.UnderwritingCallbackResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 承保完成通知的数据库事务服务，回调入口与进程内模拟承保共用。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class UnderwritingCallbackTransactionService {

    private final ApplicationRepository applicationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final ApplicationLifecycleRecordFactory recordFactory;

    /**
     * 在同一事务中写入开始轨迹、推进状态、写入审核交付事件与结束轨迹。
     *
     * <p>申请不在自动承保中时按重复通知处理，只记录复用轨迹。条件更新影响行数为 0
     * 说明并发通知已先推进状态，同样按重复处理。开始与结束轨迹同事务落库，
     * 事务回滚时不会留下没有结束的行为组。</p>
     *
     * @param command       承保完成通知命令；applicationNo 已通过校验
     * @param application   已存在的申请
     * @param interactionNo 行为组编号
     * @return 处理结果
     */
    @Transactional
    public UnderwritingCallbackResult complete(UnderwritingCompletedCommand command, ClaimApplication application,
                                               String interactionNo) {
        recordStarted(command, interactionNo);
        if (!application.isInStatus(ApplicationStatus.UNDERWRITING)) {
            recordFinished(command, interactionNo, InteractionAction.REUSED);
            return new UnderwritingCallbackResult(command.applicationNo(), UnderwritingCallbackDisposition.REUSED);
        }
        application.finishUnderwriting();
        if (!applicationRepository.updateStatus(application, ApplicationStatus.UNDERWRITING)) {
            recordFinished(command, interactionNo, InteractionAction.REUSED);
            return new UnderwritingCallbackResult(command.applicationNo(), UnderwritingCallbackDisposition.REUSED);
        }
        outboxEventRepository.insert(recordFactory.auditDeliveryRequestedEvent(application, command.traceId()));
        recordFinished(command, interactionNo, InteractionAction.COMPLETED);
        return new UnderwritingCallbackResult(command.applicationNo(), UnderwritingCallbackDisposition.COMPLETED);
    }

    private void recordStarted(UnderwritingCompletedCommand command, String interactionNo) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.UNDERWRITING_CALLBACK,
                command.applicationNo(), interactionNo, InteractionAction.STARTED, command.requestId(),
                command.traceId(), null, null));
    }

    private void recordFinished(UnderwritingCompletedCommand command, String interactionNo,
                                InteractionAction action) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.UNDERWRITING_CALLBACK,
                command.applicationNo(), interactionNo, action, command.requestId(), command.traceId(), null, null));
    }
}
