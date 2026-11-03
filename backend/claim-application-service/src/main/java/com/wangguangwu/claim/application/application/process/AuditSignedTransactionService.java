package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claimcase.ClaimCaseRelation;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.claimcase.port.ClaimCaseRelationRepository;
import com.wangguangwu.claim.application.application.exception.AuditSignedConflictException;
import com.wangguangwu.claim.application.application.command.AuditSignedCommand;
import com.wangguangwu.claim.application.application.exception.ApplicationException;
import com.wangguangwu.claim.application.application.port.ConsumedEventRepository;
import com.wangguangwu.claim.application.application.port.model.InteractionAction;
import com.wangguangwu.claim.application.application.port.model.InteractionType;
import com.wangguangwu.claim.application.application.port.InteractionLogRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审核签收的数据库事务服务，保证消费登记、案件关联追加、申请完成与行为轨迹同时生效。
 *
 * <p>等待审核的申请在签收时迁移到 {@code COMPLETED}，已完成的申请只补充缺失案件号。
 * 任一环节失败都回滚整个事务，避免出现“关系已写、状态未完成”的部分数据。</p>
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

    /** 业务时间统一使用东八区，与数据库时间字段口径一致。 */
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ConsumedEventRepository consumedEventRepository;
    private final ClaimApplicationRepository claimApplicationRepository;
    private final ClaimCaseRelationRepository claimCaseRelationRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final ClaimApplicationLifecycleRecordFactory recordFactory;

    /**
     * 登记消费、补齐案件关联，并在等待审核时完成申请。
     *
     * @param command 审核签收命令
     * @return 处理结果
     */
    @Transactional
    public AuditSignedOutcome record(AuditSignedCommand command) {
        if (!consumedEventRepository.register(CONSUMER_GROUP, command.eventId(), command.claimApplicationNo())) {
            return AuditSignedOutcome.SKIPPED;
        }
        ClaimApplication claimApplication = loadForCompletion(command);
        List<ClaimCaseRelation> missingRelations = appendMissingRelations(command);
        String interactionNo = recordFactory.newInteractionNo();
        recordStarted(command, interactionNo);
        boolean completed = completeIfAwaitingAudit(command, claimApplication);
        InteractionAction finishedAction = completed || !missingRelations.isEmpty()
                ? InteractionAction.COMPLETED : InteractionAction.REUSED;
        recordFinished(command, interactionNo, finishedAction);
        return resolveOutcome(completed, missingRelations);
    }

    /**
     * 事务内校验申请存在性、租户一致性与可签收状态。
     */
    private ClaimApplication loadForCompletion(AuditSignedCommand command) {
        ClaimApplication claimApplication = claimApplicationRepository
                .findByClaimApplicationNo(command.claimApplicationNo())
                .orElseThrow(() -> new ApplicationException("审核签收事件对应的申请不存在，claimApplicationNo=" + command.claimApplicationNo()
                                + "，eventId=" + command.eventId()));
        if (!claimApplication.getTenantCode().equals(command.tenantCode())) {
            throw new ApplicationException("审核签收消息租户与申请租户不一致，claimApplicationNo=" + command.claimApplicationNo()
                            + "，eventId=" + command.eventId());
        }
        boolean acceptable = claimApplication.isAwaitingAudit() || claimApplication.isCompleted();
        if (!acceptable || (claimApplication.isAwaitingAudit() && claimApplication.hasOpenIssue())) {
            throw new ApplicationException("申请状态不允许完成审核签收，claimApplicationNo=" + command.claimApplicationNo()
                            + "，当前状态=" + claimApplication.getStatus() + "，eventId=" + command.eventId());
        }
        return claimApplication;
    }

    /**
     * 追加尚未登记的案件号，已存在的案件号不重复插入。
     */
    private List<ClaimCaseRelation> appendMissingRelations(AuditSignedCommand command) {
        Set<String> existingCaseNos = Set.copyOf(
                claimCaseRelationRepository.findCaseNos(command.claimApplicationNo()));
        List<ClaimCaseRelation> missingRelations = command.caseNos().stream()
                .filter(caseNo -> !existingCaseNos.contains(caseNo))
                .map(caseNo -> ClaimCaseRelation.create(command.tenantCode(), command.claimApplicationNo(), caseNo))
                .toList();
        claimCaseRelationRepository.insertAll(missingRelations);
        return missingRelations;
    }

    /**
     * 等待审核的申请迁移到完成状态；已完成的申请不再保存聚合。
     */
    private boolean completeIfAwaitingAudit(AuditSignedCommand command, ClaimApplication claimApplication) {
        if (!claimApplication.isAwaitingAudit()) {
            return false;
        }
        claimApplication.completeAudit(LocalDateTime.now(BUSINESS_ZONE_ID));
        if (!claimApplicationRepository.save(claimApplication)) {
            throw new AuditSignedConflictException(command.claimApplicationNo(), command.eventId());
        }
        return true;
    }

    private AuditSignedOutcome resolveOutcome(boolean completed, List<ClaimCaseRelation> missingRelations) {
        if (completed) {
            return AuditSignedOutcome.COMPLETED;
        }
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
