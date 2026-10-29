package com.wangguangwu.claim.application.infrastructure.integration.persistence.interaction;

import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLog;
import com.wangguangwu.claim.application.domain.integration.interaction.InteractionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的交互日志仓储实现。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Repository
public class MyBatisInteractionLogRepository implements InteractionLogRepository {

    private final InteractionLogDOMapper interactionLogMapper;

    /**
     * 保存一条事件，不记录申请人员等敏感请求正文。
     *
     * @param interactionLog 已发生事件
     */
    @Override
    public void insert(InteractionLog interactionLog) {
        InteractionLogDO interactionLogDO = InteractionLogDO.builder().claimApplicationNo(interactionLog.claimApplicationNo())
                .interactionNo(interactionLog.interactionNo()).interactionType(interactionLog.interactionType())
                .action(interactionLog.action()).direction(interactionLog.direction())
                .targetSystem(interactionLog.targetSystem()).requestId(interactionLog.requestId())
                .traceId(interactionLog.traceId()).resultStatus(interactionLog.resultStatus())
                .errorCode(interactionLog.errorCode()).errorMessage(interactionLog.errorMessage())
                .occurredAt(interactionLog.occurredAt()).build();
        interactionLogMapper.insert(interactionLogDO);
    }
}
