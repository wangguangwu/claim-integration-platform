package com.wangguangwu.claim.application.infrastructure.integration.persistence.interaction;

import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLog;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的交互日志仓储实现。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Repository
public class MyBatisInteractionLogRepository implements InteractionLogRepository {

    private final InteractionLogDOMapper interactionLogMapper;

    public MyBatisInteractionLogRepository(InteractionLogDOMapper interactionLogMapper) {
        this.interactionLogMapper = interactionLogMapper;
    }

    /**
     * 保存一条事件，不记录申请人员等敏感请求正文。
     *
     * @param interactionLog 已发生事件
     */
    @Override
    public void insert(InteractionLog interactionLog) {
        InteractionLogDO interactionLogDO = new InteractionLogDO();
        interactionLogDO.setApplicationNo(interactionLog.applicationNo());
        interactionLogDO.setInteractionNo(interactionLog.interactionNo());
        interactionLogDO.setInteractionType(interactionLog.interactionType());
        interactionLogDO.setAction(interactionLog.action());
        interactionLogDO.setDirection(interactionLog.direction());
        interactionLogDO.setTargetSystem(interactionLog.targetSystem());
        interactionLogDO.setRequestId(interactionLog.requestId());
        interactionLogDO.setTraceId(interactionLog.traceId());
        interactionLogDO.setResultStatus(interactionLog.resultStatus());
        interactionLogDO.setErrorCode(interactionLog.errorCode());
        interactionLogDO.setErrorMessage(interactionLog.errorMessage());
        interactionLogDO.setOccurredAt(interactionLog.occurredAt());
        interactionLogMapper.insert(interactionLogDO);
    }
}
