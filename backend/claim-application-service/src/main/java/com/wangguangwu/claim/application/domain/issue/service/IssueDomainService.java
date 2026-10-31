package com.wangguangwu.claim.application.domain.issue.service;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.event.IssueClosedEvent;
import com.wangguangwu.claim.application.domain.issue.event.IssueRaisedEvent;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 问题件领域服务，协调问题件聚合与申请聚合的跨聚合操作。
 *
 * <p>职责：
 * <ul>
 *   <li>封装跨聚合的业务规则校验</li>
 *   <li>协调两个聚合的状态变更</li>
 *   <li>发布领域事件</li>
 * </ul>
 *
 * <p>领域服务不包含事务管理和幂等判断，这些是应用层（用例）的关注点。</p>
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
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 下发问题件，协调问题件创建与申请状态变更。
     *
     * <p>业务规则：
     * <ul>
     *   <li>申请必须存在</li>
     *   <li>申请状态必须为 WAIT_AUDIT</li>
     *   <li>同一申请不能已有 OPEN 状态的问题件</li>
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

        if (!application.isWaitAudit()) {
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

        application.transitionToIssue();
        claimApplicationRepository.updateStatus(application, application.getStatus());

        IssueRaisedEvent event = new IssueRaisedEvent(
                issueNo, claimApplicationNo, reason, auditor, LocalDateTime.now());
        eventPublisher.publishEvent(event);

        log.info("问题件下发完成，issueNo={}，claimApplicationNo={}", issueNo, claimApplicationNo);
        return issue;
    }

    /**
     * 关闭问题件，协调问题件状态变更与申请状态回退。
     *
     * <p>业务规则：
     * <ul>
     *   <li>问题件必须存在</li>
     *   <li>问题件关联的申请必须存在</li>
     * </ul>
     *
     * @param issueNo 问题件编号
     * @param auditor 处理人
     * @return 关闭的问题件
     * @throws ClaimApplicationIssueException 当业务规则校验失败时
     */
    public ClaimApplicationIssue closeIssue(String issueNo, String auditor) {
        ClaimApplicationIssue issue = claimApplicationIssueRepository
                .findByIssueNo(issueNo)
                .orElseThrow(() -> new ClaimApplicationIssueException(
                        ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND,
                        "问题件不存在，issueNo=" + issueNo));

        if (issue.isClosed()) {
            log.info("问题件已关闭，按幂等处理，issueNo={}，claimApplicationNo={}", issueNo,
                    issue.getClaimApplicationNo());
            return issue;
        }

        issue.close(auditor);
        claimApplicationIssueRepository.update(issue);

        ClaimApplication application = claimApplicationRepository
                .findByClaimApplicationNo(issue.getClaimApplicationNo())
                .orElseThrow(() -> new ClaimApplicationIssueException(
                        ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND,
                        "问题件关联的申请不存在，issueNo=" + issueNo
                                + "，claimApplicationNo=" + issue.getClaimApplicationNo()));

        application.transitionToWaitAudit();
        claimApplicationRepository.updateStatus(application, application.getStatus());

        IssueClosedEvent event = new IssueClosedEvent(
                issueNo, issue.getClaimApplicationNo(), auditor, LocalDateTime.now());
        eventPublisher.publishEvent(event);

        log.info("问题件关闭完成，issueNo={}，claimApplicationNo={}", issueNo, issue.getClaimApplicationNo());
        return issue;
    }
}
