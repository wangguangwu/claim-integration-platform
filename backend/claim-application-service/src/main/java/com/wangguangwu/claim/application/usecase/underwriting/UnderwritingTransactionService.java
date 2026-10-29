package com.wangguangwu.claim.application.usecase.underwriting;

import com.wangguangwu.claim.application.domain.claim.port.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.domain.integration.outbox.port.OutboxEventRepository;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 承保分流的数据库事务服务，只推进状态、写入审核交付事件与外部调用轨迹。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class UnderwritingTransactionService {

    private final ApplicationRepository applicationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final ApplicationLifecycleRecordFactory recordFactory;

    /**
     * 把有有效保单的申请推进到等待审核签收，并写入审核交付事件。
     *
     * @param application 处于已受理状态的申请
     * @param traceId     链路标识
     * @return 推进成功时为 true；状态已被其他执行者改变时为 false
     */
    @Transactional
    public boolean promoteToWaitAudit(ClaimApplication application, String traceId) {
        application.waitAudit();
        if (!applicationRepository.updateStatus(application, ApplicationStatus.ACCEPTED)) {
            return false;
        }
        outboxEventRepository.insert(recordFactory.auditDeliveryRequestedEvent(application, traceId));
        return true;
    }

    /**
     * 把无有效保单的申请推进到自动承保。
     *
     * @param application 处于已受理状态的申请
     * @return 推进成功时为 true；状态已被其他执行者改变时为 false
     */
    @Transactional
    public boolean startUnderwriting(ClaimApplication application) {
        application.startUnderwriting();
        return applicationRepository.updateStatus(application, ApplicationStatus.ACCEPTED);
    }

    /**
     * 把一次承保外部调用的开始与结束轨迹在同一事务落库。
     *
     * <p>外部调用在事务外执行，调用中断时不会提交任何轨迹行，因此不会出现只有开始、
     * 没有结束的孤儿行为组。</p>
     *
     * @param applicationNo  平台申请编号
     * @param interactionNo  行为组编号
     * @param terminalAction 本次调用的结束动作
     * @param traceId        链路标识
     * @param errorCode      失败错误码，成功时为 null
     * @param errorMessage   失败说明，成功时为 null
     */
    @Transactional
    public void recordAttempt(String applicationNo, String interactionNo, InteractionAction terminalAction,
                              String traceId, String errorCode, String errorMessage) {
        insert(applicationNo, interactionNo, InteractionAction.STARTED, traceId, null, null);
        insert(applicationNo, interactionNo, terminalAction, traceId, errorCode, errorMessage);
    }

    private void insert(String applicationNo, String interactionNo, InteractionAction action, String traceId,
                        String errorCode, String errorMessage) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.UNDERWRITING, applicationNo,
                interactionNo, action, null, traceId, errorCode, errorMessage));
    }
}
