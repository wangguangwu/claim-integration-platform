package com.wangguangwu.claim.application.usecase.issue;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueException;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueCloseCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueCloseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 问题件关闭用例：校验问题件状态、幂等判断、关闭问题件并回退申请状态。
 *
 * <p>整个用例在一个事务内完成，保证问题件状态变更与申请状态回退的原子性。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ClaimApplicationIssueCloseUseCase {

    private final ClaimApplicationIssueRepository claimApplicationIssueRepository;
    private final ClaimApplicationRepository claimApplicationRepository;

    /**
     * 处理一次问题件关闭请求。
     *
     * @param command 问题件关闭命令
     * @return 关闭结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ClaimApplicationIssueCloseResult close(ClaimApplicationIssueCloseCommand command) {
        ClaimApplicationIssue issue = claimApplicationIssueRepository
                .findByIssueNo(command.issueNo())
                .orElseThrow(() -> new ClaimApplicationIssueException(
                        ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND,
                        "问题件不存在，issueNo=" + command.issueNo()));

        if (issue.isClosed()) {
            log.info("问题件已关闭，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    issue.getClaimApplicationNo());
            return new ClaimApplicationIssueCloseResult(command.issueNo(), false);
        }

        issue.close(command.auditor());
        claimApplicationIssueRepository.update(issue);

        ClaimApplication claimApplication = claimApplicationRepository
                .findByClaimApplicationNo(issue.getClaimApplicationNo())
                .orElseThrow(() -> new ClaimApplicationIssueException(
                        ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND,
                        "问题件关联的申请不存在，issueNo=" + command.issueNo()
                                + "，claimApplicationNo=" + issue.getClaimApplicationNo()));

        claimApplication.transitionToWaitAudit();
        claimApplicationRepository.updateStatus(claimApplication, claimApplication.getStatus());

        log.info("问题件关闭成功，issueNo={}，claimApplicationNo={}", command.issueNo(), issue.getClaimApplicationNo());
        return new ClaimApplicationIssueCloseResult(command.issueNo(), true);
    }
}
