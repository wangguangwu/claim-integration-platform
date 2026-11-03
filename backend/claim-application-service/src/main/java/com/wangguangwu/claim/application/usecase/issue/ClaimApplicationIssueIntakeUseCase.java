package com.wangguangwu.claim.application.usecase.issue;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.domain.exception.DomainException;
import com.wangguangwu.claim.application.usecase.intake.port.ClaimApplicationIntakeSnapshotQuery;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueIntakeCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueIntakeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 问题件下发用例：幂等判断、聚合加载与保存冲突处理。
 *
 * <p>问题件是申请聚合内实体，打开问题件由根统一迁移申请状态；保存冲突在事务之外重新读取，
 * 相同问题件编号按幂等返回，其余情况按状态冲突抛出业务异常。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ClaimApplicationIssueIntakeUseCase {

    private final ClaimApplicationRepository claimApplicationRepository;
    private final ClaimApplicationIssueTransactionService transactionService;
    private final ClaimApplicationIntakeSnapshotQuery intakeSnapshotQuery;

    /**
     * 处理一次问题件下发请求。
     *
     * @param command 问题件下发命令
     * @return 下发结果
     */
    public ClaimApplicationIssueIntakeResult receive(ClaimApplicationIssueIntakeCommand command) {
        if (claimApplicationRepository.findByIssueNo(command.issueNo()).isPresent()) {
            log.info("问题件已存在，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    command.claimApplicationNo());
            return idempotentResult(command);
        }
        ClaimApplication claimApplication = claimApplicationRepository
                .findByClaimApplicationNo(command.claimApplicationNo())
                .orElseThrow(() -> new DomainException(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND,
                        "申请不存在，claimApplicationNo=" + command.claimApplicationNo()));
        claimApplication.raiseIssue(command.issueNo(), command.reason(), command.auditor(),
                command.issueContent());
        return saveOrResolveConflict(claimApplication, command);
    }

    private ClaimApplicationIssueIntakeResult saveOrResolveConflict(ClaimApplication claimApplication,
                                                                    ClaimApplicationIssueIntakeCommand command) {
        try {
            if (transactionService.save(claimApplication)) {
                markMutable(claimApplication);
                log.info("问题件下发成功，issueNo={}，claimApplicationNo={}", command.issueNo(),
                        command.claimApplicationNo());
                return new ClaimApplicationIssueIntakeResult(command.issueNo(), command.claimApplicationNo(), true);
            }
        } catch (DuplicateKeyException exception) {
            log.warn("问题件并发创建触发唯一约束，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    command.claimApplicationNo(), exception);
            return idempotentResult(command);
        }
        return resolveAfterConflict(command);
    }

    private ClaimApplicationIssueIntakeResult resolveAfterConflict(ClaimApplicationIssueIntakeCommand command) {
        if (claimApplicationRepository.findByIssueNo(command.issueNo()).isPresent()) {
            log.info("问题件并发下发已落库，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    command.claimApplicationNo());
            return idempotentResult(command);
        }
        ClaimApplication current = claimApplicationRepository.findByClaimApplicationNo(command.claimApplicationNo())
                .orElseThrow(() -> new DomainException(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_NOT_FOUND,
                        "申请不存在，claimApplicationNo=" + command.claimApplicationNo()));
        current.raiseIssue(command.issueNo(), command.reason(), command.auditor(), command.issueContent());
        if (transactionService.save(current)) {
            markMutable(current);
            log.info("问题件重试下发成功，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    command.claimApplicationNo());
            return new ClaimApplicationIssueIntakeResult(command.issueNo(), command.claimApplicationNo(), true);
        }
        throw new DomainException(ClaimApplicationIssueErrorCode.ISSUE_APPLICATION_STATUS_INVALID,
                "申请状态已被并发改变，无法下发问题件，claimApplicationNo=" + command.claimApplicationNo());
    }

    /**
     * 下发成功后把签收可复用快照降级为状态可变，失效失败不回滚已提交的问题件。
     */
    private void markMutable(ClaimApplication claimApplication) {
        try {
            intakeSnapshotQuery.markMutable(claimApplication.getTenantCode(),
                    claimApplication.getExternalClaimApplicationNo());
        } catch (RuntimeException exception) {
            log.warn("问题件下发后签收快照失效失败，claimApplicationNo={}，externalClaimApplicationNo={}",
                    claimApplication.getClaimApplicationNo(), claimApplication.getExternalClaimApplicationNo(),
                    exception);
        }
    }

    private ClaimApplicationIssueIntakeResult idempotentResult(ClaimApplicationIssueIntakeCommand command) {
        return new ClaimApplicationIssueIntakeResult(command.issueNo(), command.claimApplicationNo(), false);
    }
}
