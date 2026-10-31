package com.wangguangwu.claim.application.domain.issue.service;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.ClaimApplicationStatus;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.event.IssueClosedEvent;
import com.wangguangwu.claim.application.domain.issue.event.IssueDomainEventPublisher;
import com.wangguangwu.claim.application.domain.issue.event.IssueRaisedEvent;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 问题件领域服务，协调问题件聚合与申请聚合的跨聚合规则与状态变更。
 *
 * <p>职责：
 * <ul>
 *   <li>校验跨聚合的业务规则</li>
 *   <li>协调两个聚合的状态变更并以迁移前状态做条件更新</li>
 *   <li>通过领域事件端口发布问题件事实</li>
 * </ul>
 *
 * <p>领域服务不包含事务管理和幂等判断，两者都是应用层（用例）的关注点。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class IssueDomainService {

    private final ClaimApplicationRepository claimApplicationRepository;
    private final ClaimApplicationIssueRepository claimApplicationIssueRepository;
    private final IssueDomainEventPublisher issueDomainEventPublisher;

    /**
     * 下发问题件，协调问题件创建与申请挂起。
     *
     * <p>业务规则：
     * <ul>
     *   <li>申请必须存在</li>
     *   <li>申请必须处于等待审核</li>
     *   <li>同一申请不能已有未关闭问题件</li>
     * </ul>
     *
     * @param claimApplicationNo 平台申请编号
     * @param issueNo            问题件编号
     * @param reason             问题原因
     * @param auditor            审核人员
     * @param issueContent       问题详情 JSON
     * @return 创建的问题件
     * @throws ClaimApplicationIssueException 当业务规则校验失败时
     */
    public ClaimApplicationIssue raiseIssue(String claimApplicationNo, String issueNo, String reason,
                                            String auditor, String issueContent) {
        ClaimApplication application = claimApplicationRepository
                .findByClaimApplicationNo(claimApplicationNo)
                .orElseThrow(() -> new ClaimApplicationIssueException(
                        ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND,
                        "申请不存在，claimApplicationNo=" + claimApplicationNo));

        if (!application.isAwaitingAudit()) {
            throw new ClaimApplicationIssueException(
                    ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID,
                    "申请状态不合法，无法下发问题件，claimApplicationNo=" + claimApplicationNo
                            + "，当前状态=" + application.getStatus());
        }

        if (claimApplicationIssueRepository.hasOpenIssue(claimApplicationNo)) {
            throw new ClaimApplicationIssueException(
                    ClaimApplicationIssueErrorCode.ISSUE_ALREADY_OPEN,
                    "同一申请已有未关闭问题件，claimApplicationNo=" + claimApplicationNo);
        }

        ClaimApplicationIssue issue = ClaimApplicationIssue.create(
                claimApplicationNo, issueNo, reason, auditor, issueContent);
        claimApplicationIssueRepository.insert(issue);

        ClaimApplicationStatus statusBeforeIssue = application.getStatus();
        application.enterIssue();
        persistTransition(application, statusBeforeIssue, claimApplicationNo);

        issueDomainEventPublisher.publish(new IssueRaisedEvent(
                issueNo, claimApplicationNo, reason, auditor, LocalDateTime.now()));

        log.info("问题件下发完成，issueNo={}，claimApplicationNo={}", issueNo, claimApplicationNo);
        return issue;
    }

    /**
     * 关闭问题件，协调问题件关闭与申请恢复审核。
     *
     * <p>业务规则：问题件必须处于未关闭状态，关联申请必须存在；问题件是否已关闭的幂等判断由用例负责。</p>
     *
     * @param issue   已加载的问题件
     * @param auditor 处理人
     * @return 关闭后的问题件
     * @throws ClaimApplicationIssueException 当关联申请不存在或状态已被并发改变时
     */
    public ClaimApplicationIssue closeIssue(ClaimApplicationIssue issue, String auditor) {
        issue.close(auditor);
        claimApplicationIssueRepository.update(issue);

        ClaimApplication application = claimApplicationRepository
                .findByClaimApplicationNo(issue.getClaimApplicationNo())
                .orElseThrow(() -> new ClaimApplicationIssueException(
                        ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND,
                        "问题件关联的申请不存在，issueNo=" + issue.getIssueNo()
                                + "，claimApplicationNo=" + issue.getClaimApplicationNo()));

        ClaimApplicationStatus statusBeforeClose = application.getStatus();
        application.resumeAudit();
        persistTransition(application, statusBeforeClose, issue.getClaimApplicationNo());

        issueDomainEventPublisher.publish(new IssueClosedEvent(
                issue.getIssueNo(), issue.getClaimApplicationNo(), auditor, LocalDateTime.now()));

        log.info("问题件关闭完成，issueNo={}，claimApplicationNo={}", issue.getIssueNo(), issue.getClaimApplicationNo());
        return issue;
    }

    /**
     * 以迁移前状态为条件推进申请状态，条件不命中说明状态已被并发改写。
     *
     * <p>此时抛出业务异常让整个用例回滚，避免问题件与申请状态出现不一致。</p>
     *
     * @param application     已变更的申请聚合
     * @param statusBefore    迁移前的状态
     * @param claimApplicationNo 平台申请编号
     */
    private void persistTransition(ClaimApplication application, ClaimApplicationStatus statusBefore,
                                   String claimApplicationNo) {
        if (!claimApplicationRepository.updateStatus(application, statusBefore)) {
            throw new ClaimApplicationIssueException(
                    ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID,
                    "申请状态已被并发改变，无法完成问题件状态同步，claimApplicationNo=" + claimApplicationNo
                            + "，迁移前状态=" + statusBefore);
        }
    }
}
