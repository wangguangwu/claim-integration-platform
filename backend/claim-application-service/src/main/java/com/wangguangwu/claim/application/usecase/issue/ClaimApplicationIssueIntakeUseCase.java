package com.wangguangwu.claim.application.usecase.issue;

import com.wangguangwu.claim.application.domain.issue.port.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.service.IssueDomainService;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueIntakeCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueIntakeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 问题件下发用例：幂等判断、事务管理、委托领域服务执行业务逻辑。
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
public class ClaimApplicationIssueIntakeUseCase {

    private final IssueDomainService issueDomainService;
    private final ClaimApplicationIssueRepository claimApplicationIssueRepository;

    /**
     * 处理一次问题件下发请求。
     *
     * @param command 问题件下发命令
     * @return 下发结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ClaimApplicationIssueIntakeResult receive(ClaimApplicationIssueIntakeCommand command) {
        if (claimApplicationIssueRepository.findByIssueNo(command.issueNo()).isPresent()) {
            log.info("问题件已存在，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    command.claimApplicationNo());
            return new ClaimApplicationIssueIntakeResult(command.issueNo(), command.claimApplicationNo(), false);
        }

        try {
            issueDomainService.raiseIssue(
                    command.claimApplicationNo(),
                    command.issueNo(),
                    command.reason(),
                    command.auditor(),
                    command.issueContent()
            );
        } catch (DuplicateKeyException exception) {
            log.warn("问题件并发创建触发唯一约束，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    command.claimApplicationNo(), exception);
            return new ClaimApplicationIssueIntakeResult(command.issueNo(), command.claimApplicationNo(), false);
        }

        log.info("问题件下发成功，issueNo={}，claimApplicationNo={}", command.issueNo(), command.claimApplicationNo());
        return new ClaimApplicationIssueIntakeResult(command.issueNo(), command.claimApplicationNo(), true);
    }
}
