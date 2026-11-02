package com.wangguangwu.claim.application.usecase.audit;

import com.wangguangwu.claim.application.domain.claim.ClaimCaseRelation;
import com.wangguangwu.claim.application.domain.claim.port.ClaimCaseRelationRepository;
import com.wangguangwu.claim.application.usecase.integration.consumption.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionAction;
import com.wangguangwu.claim.application.usecase.integration.interaction.port.InteractionLogRepository;
import com.wangguangwu.claim.application.usecase.integration.interaction.model.InteractionType;
import com.wangguangwu.claim.application.usecase.audit.command.AuditSignedCommand;
import com.wangguangwu.claim.application.usecase.audit.result.AuditSignedOutcome;
import com.wangguangwu.claim.application.usecase.integration.record.ClaimApplicationLifecycleRecordFactory;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审核签收的数据库事务服务，保证消费登记、案件关联与行为轨迹同时生效。
 *
 * <p>审核签收只记录案件关联事实，不改变申请状态。重复事件由消费登记拦截；
 * 新事件中已存在的案件号不重复插入，只补充缺失项。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AuditSignedTransactionService {

    /**
     * 审核签收消费者组，与绑定配置及死信主题约定一致。
     */
    public static final String CONSUMER_GROUP = "claim-application-audit-signed-v1";

    private final ConsumedEventRepository consumedEventRepository;
    private final ClaimCaseRelationRepository claimCaseRelationRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;

    /**
     * 登记消费并补齐案件关联，同时写入开始与结束行为轨迹。
     *
     * @param command 审核签收命令
     * @return 处理结果
     */
    @Transactional
    public AuditSignedOutcome record(AuditSignedCommand command) {
        if (!consumedEventRepository.register(CONSUMER_GROUP, command.eventId(), command.claimApplicationNo())) {
            return AuditSignedOutcome.SKIPPED;
        }
        Set<String> existingCaseNos = Set.copyOf(
                claimCaseRelationRepository.findCaseNos(command.claimApplicationNo()));
        List<ClaimCaseRelation> missingRelations = command.caseNos().stream()
                .filter(caseNo -> !existingCaseNos.contains(caseNo))
                .map(caseNo -> ClaimCaseRelation.create(command.tenantCode(), command.claimApplicationNo(), caseNo))
                .toList();
        claimCaseRelationRepository.insertAll(missingRelations);

        String interactionNo = recordFactory.newInteractionNo();
        recordStarted(command, interactionNo);
        recordFinished(command, interactionNo,
                missingRelations.isEmpty() ? InteractionAction.REUSED : InteractionAction.COMPLETED);
        return missingRelations.isEmpty() ? AuditSignedOutcome.REUSED : AuditSignedOutcome.RECORDED;
    }

    private void recordStarted(AuditSignedCommand command, String interactionNo) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.AUDIT_SIGNED,
                command.claimApplicationNo(), interactionNo, InteractionAction.STARTED, command.eventId(),
                command.traceId(), null, null));
    }

    private void recordFinished(AuditSignedCommand command, String interactionNo, InteractionAction action) {
        interactionLogRepository.insert(recordFactory.interaction(InteractionType.AUDIT_SIGNED,
                command.claimApplicationNo(), interactionNo, action, command.eventId(), command.traceId(), null,
                null));
    }
}
