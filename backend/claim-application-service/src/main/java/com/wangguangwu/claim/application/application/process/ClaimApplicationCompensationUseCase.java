package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.application.process.compensation.AcceptedCompensationHandler;
import com.wangguangwu.claim.application.application.process.compensation.OutboxSendingCompensationHandler;
import com.wangguangwu.claim.application.application.process.compensation.PendingAcceptCompensationHandler;
import com.wangguangwu.claim.application.application.process.compensation.UnderwritingCompensationHandler;
import com.wangguangwu.claim.application.application.process.compensation.WaitAuditCompensationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 补偿用例入口：按滞留状态委派给对应补偿处理器。
 *
 * <p>每个滞留状态由独立处理器承载，本类只保留统一入口，便于调度器按固定顺序触发。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationCompensationUseCase {

    private final OutboxSendingCompensationHandler outboxSendingCompensationHandler;
    private final PendingAcceptCompensationHandler pendingAcceptCompensationHandler;
    private final AcceptedCompensationHandler acceptedCompensationHandler;
    private final UnderwritingCompensationHandler underwritingCompensationHandler;
    private final WaitAuditCompensationHandler waitAuditCompensationHandler;

    /**
     * 把停留过久的发送中事件重置为待投递，处理投递进程崩溃场景。
     *
     * @return 重置条数
     */
    public int resetStuckSendingEvents() {
        return outboxSendingCompensationHandler.reset();
    }

    /**
     * 对待受理滞留的申请重放材料处理。
     *
     * @return 尝试补偿的申请条数
     */
    public int redrivePendingAcceptClaimApplications() {
        return pendingAcceptCompensationHandler.redrive();
    }

    /**
     * 对已受理滞留的申请重试承保分流。
     *
     * @return 尝试补偿的申请条数
     */
    public int redispatchAcceptedClaimApplications() {
        return acceptedCompensationHandler.redispatch();
    }

    /**
     * 对承保中滞留的申请查询承保结果。
     *
     * @return 尝试补偿的申请条数
     */
    public int recoverUnderwritingClaimApplications() {
        return underwritingCompensationHandler.recover();
    }

    /**
     * 对等待审核滞留的申请查询签收状态。
     *
     * @return 尝试补偿的申请条数
     */
    public int recoverWaitAuditClaimApplications() {
        return waitAuditCompensationHandler.recover();
    }
}
