package com.wangguangwu.claim.application.usecase.issue;

import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.port.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.issue.service.IssueDomainService;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueCloseCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueCloseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 问题件关闭用例：幂等判断、事务管理、委托领域服务执行业务逻辑。
 *
 * <p>职责：
 * <ul>
 *   <li>幂等性检查（应用层关注点）</li>
 *   <li>事务管理（应用层关注点）</li>
 *   <li>委托领域服务执行跨聚合业务规则</li>
 * </ul>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ClaimApplicationIssueCloseUseCase {

    private final IssueDomainService issueDomainService;
    private final ClaimApplicationIssueRepository claimApplicationIssueRepository;

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
                .orElseThrow(() -> new BusinessException(
                        ClaimApplicationIssueErrorCode.ISSUE_NOT_FOUND,
                        "问题件不存在，issueNo=" + command.issueNo()));

        if (issue.isClosed()) {
            log.info("问题件已关闭，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    issue.getClaimApplicationNo());
            return new ClaimApplicationIssueCloseResult(command.issueNo(), false);
        }

        issueDomainService.closeIssue(issue, command.auditor());

        log.info("问题件关闭成功，issueNo={}，claimApplicationNo={}", command.issueNo(), issue.getClaimApplicationNo());
        return new ClaimApplicationIssueCloseResult(command.issueNo(), true);
    }
}
