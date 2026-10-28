package com.wangguangwu.claim.application.usecase;

import com.wangguangwu.claim.application.domain.application.Application;
import com.wangguangwu.claim.application.domain.application.ApplicationRepository;
import com.wangguangwu.claim.application.domain.application.ApplicationStatus;
import com.wangguangwu.claim.application.domain.outbox.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 承保分流的数据库事务服务，只推进状态与写入审核交付事件。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Service
public class UnderwritingTransactionService {

    private final ApplicationRepository applicationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationLifecycleRecordFactory recordFactory;

    public UnderwritingTransactionService(ApplicationRepository applicationRepository,
                                          OutboxEventRepository outboxEventRepository,
                                          ApplicationLifecycleRecordFactory recordFactory) {
        this.applicationRepository = applicationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.recordFactory = recordFactory;
    }

    /**
     * 把有有效保单的申请推进到等待审核签收，并写入审核交付事件。
     *
     * @param application 处于已受理状态的申请
     * @param traceId     链路标识
     * @return 推进成功时为 true；状态已被其他执行者改变时为 false
     */
    @Transactional
    public boolean promoteToWaitAudit(Application application, String traceId) {
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
    public boolean startUnderwriting(Application application) {
        application.startUnderwriting();
        return applicationRepository.updateStatus(application, ApplicationStatus.ACCEPTED);
    }
}
