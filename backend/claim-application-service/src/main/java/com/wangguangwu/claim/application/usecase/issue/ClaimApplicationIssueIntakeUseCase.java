package com.wangguangwu.claim.application.usecase.issue;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueException;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueIntakeCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueIntakeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 问题件下发用例：校验申请状态、幂等判断、创建问题件并推进申请状态。
 *
 * <p>整个用例在一个事务内完成，保证问题件创建与申请状态变更的原子性。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ClaimApplicationIssueIntakeUseCase {

    private final ClaimApplicationRepository claimApplicationRepository;
    private final ClaimApplicationIssueRepository claimApplicationIssueRepository;

    /**
     * 处理一次问题件下发请求。
     *
     * @param command 问题件下发命令
     * @return 下发结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ClaimApplicationIssueIntakeResult receive(ClaimApplicationIssueIntakeCommand command) {
        ClaimApplication claimApplication = claimApplicationRepository
                .findByClaimApplicationNo(command.claimApplicationNo())
                .orElseThrow(() -> new ClaimApplicationIssueException(
                        ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND,
                        "申请不存在，claimApplicationNo=" + command.claimApplicationNo()));

        if (!claimApplication.isWaitAudit()) {
            throw new ClaimApplicationIssueException(
                    ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID,
                    "申请状态不合法，无法下发问题件，claimApplicationNo=" + command.claimApplicationNo()
                            + "，当前状态=" + claimApplication.getStatus());
        }

        if (claimApplicationIssueRepository.findByIssueNo(command.issueNo()).isPresent()) {
            log.info("问题件已存在，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    command.claimApplicationNo());
            return new ClaimApplicationIssueIntakeResult(command.issueNo(), command.claimApplicationNo(), false);
        }

        if (claimApplicationIssueRepository.hasOpenIssue(command.claimApplicationNo())) {
            throw new ClaimApplicationIssueException(
                    ClaimApplicationIssueErrorCode.ISSUE_ALREADY_OPEN,
                    "同一申请已有未关闭问题件，claimApplicationNo=" + command.claimApplicationNo());
        }

        ClaimApplicationIssue issue = ClaimApplicationIssue.create(
                command.claimApplicationNo(),
                command.issueNo(),
                command.reason(),
                command.auditor(),
                command.issueContent()
        );

        try {
            claimApplicationIssueRepository.insert(issue);
        } catch (DuplicateKeyException exception) {
            log.warn("问题件并发创建触发唯一约束，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    command.claimApplicationNo(), exception);
            return new ClaimApplicationIssueIntakeResult(command.issueNo(), command.claimApplicationNo(), false);
        }

        claimApplication.transitionToIssue();
        claimApplicationRepository.updateStatus(claimApplication, claimApplication.getStatus());

        log.info("问题件下发成功，issueNo={}，claimApplicationNo={}", command.issueNo(), command.claimApplicationNo());
        return new ClaimApplicationIssueIntakeResult(command.issueNo(), command.claimApplicationNo(), true);
    }
}
