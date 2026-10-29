package com.wangguangwu.claim.application.usecase.integration.compensation;

import com.wangguangwu.claim.application.domain.integration.interaction.InteractionAction;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionType;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 滞留补偿的轨迹事务服务：一次外部调用重放的开始与结束轨迹同事务落库。
 *
 * <p>补偿在事务外重放承保查询与自动承保请求，调用中断时不能留下只有开始的行为组，
 * 因此开始与结束轨迹都由本服务在调用结束后一次性写入。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationCompensationTransactionService {

    private final InteractionLogRepository interactionLogRepository;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;

    /**
     * 在同一事务中写入一次补偿重放的开始与结束轨迹。
     *
     * @param claimApplicationNo  平台申请编号
     * @param interactionNo  行为组编号
     * @param requestId      补偿请求标识，用于与承保回调对账
     * @param terminalAction 本次重放的结束动作
     * @param traceId        链路标识
     * @param errorCode      失败错误码，成功时为 null
     * @param errorMessage   失败说明，成功时为 null
     */
    @Transactional
    public void recordAttempt(String claimApplicationNo, String interactionNo, String requestId,
                              InteractionAction terminalAction, String traceId, String errorCode,
                              String errorMessage) {
        insert(claimApplicationNo, interactionNo, requestId, InteractionAction.STARTED, traceId, null, null);
        insert(claimApplicationNo, interactionNo, requestId, terminalAction, traceId, errorCode, errorMessage);
    }

    private void insert(String claimApplicationNo, String interactionNo, String requestId, InteractionAction action,
                        String traceId, String errorCode, String errorMessage) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.UNDERWRITING, claimApplicationNo,
                interactionNo, action, requestId, traceId, errorCode, errorMessage));
    }
}
