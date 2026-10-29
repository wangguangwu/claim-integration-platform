package com.wangguangwu.claim.application.usecase.underwriting;

import com.wangguangwu.claim.application.domain.claim.ApplicationRepository;
import com.wangguangwu.claim.application.domain.claim.ApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.integration.outbox.OutboxEventRepository;
import com.wangguangwu.claim.application.usecase.integration.record.ApplicationLifecycleRecordFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 承保分流的数据库事务服务，只推进状态与写入审核交付事件。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Service
public class UnderwritingTransactionService {

    private final ApplicationRepository applicationRepository;
    private final OutboxEventRepository outboxEventRepository;
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
}
