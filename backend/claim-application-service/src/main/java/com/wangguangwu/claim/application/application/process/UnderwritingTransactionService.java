package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.application.port.model.InteractionAction;
import com.wangguangwu.claim.application.application.port.InteractionLogRepository;
import com.wangguangwu.claim.application.application.port.model.InteractionType;
import com.wangguangwu.claim.application.application.port.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 承保分流的数据库事务服务，只推进聚合状态、写入审核交付事件与外部调用轨迹。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class UnderwritingTransactionService {

    private final ClaimApplicationRepository claimApplicationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;

    /**
     * 把有有效保单的申请推进到等待审核签收，并在保存成功后写入审核交付事件。
     *
     * @param claimApplication 处于已受理状态的申请
     * @param traceId     链路标识
     * @return 推进成功时为 true；状态已被其他执行者改变时为 false
     */
    @Transactional
    public boolean promoteToWaitAudit(ClaimApplication claimApplication, String traceId) {
        claimApplication.waitAudit();
        if (!claimApplicationRepository.save(claimApplication)) {
            return false;
        }
        outboxEventRepository.insert(recordFactory.auditDeliveryRequestedEvent(claimApplication, traceId));
        return true;
    }

    /**
     * 把无有效保单的申请推进到自动承保。
     *
     * @param claimApplication 处于已受理状态的申请
     * @return 推进成功时为 true；状态已被其他执行者改变时为 false
     */
    @Transactional
    public boolean startUnderwriting(ClaimApplication claimApplication) {
        claimApplication.startUnderwriting();
        return claimApplicationRepository.save(claimApplication);
    }

    /**
     * 把一次承保外部调用的开始与结束轨迹在同一事务落库。
     *
     * <p>外部调用在事务外执行，调用中断时不会提交任何轨迹行，因此不会出现只有开始、
     * 没有结束的孤儿行为组。</p>
     *
     * @param claimApplicationNo  平台申请编号
     * @param interactionNo  行为组编号
     * @param terminalAction 本次调用的结束动作
     * @param traceId        链路标识
     * @param errorCode      失败错误码，成功时为 null
     * @param errorMessage   失败说明，成功时为 null
     */
    @Transactional
    public void recordAttempt(String claimApplicationNo, String interactionNo, InteractionAction terminalAction,
                              String traceId, String errorCode, String errorMessage) {
        insert(claimApplicationNo, interactionNo, InteractionAction.STARTED, traceId, null, null);
        insert(claimApplicationNo, interactionNo, terminalAction, traceId, errorCode, errorMessage);
    }

    private void insert(String claimApplicationNo, String interactionNo, InteractionAction action, String traceId,
                        String errorCode, String errorMessage) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.UNDERWRITING, claimApplicationNo,
                interactionNo, action, null, traceId, errorCode, errorMessage));
    }
}
