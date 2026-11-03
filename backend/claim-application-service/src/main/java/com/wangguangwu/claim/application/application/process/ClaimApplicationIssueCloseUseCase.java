package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import com.wangguangwu.claim.application.shared.exception.DomainException;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIssueCloseCommand;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 问题件关闭用例：定位聚合、调用根关闭问题件并处理保存冲突。
 *
 * <p>已关闭的问题件由根直接按幂等返回 {@code false}；保存冲突在事务之外重新读取，
 * 若并发执行者已关闭则按幂等返回，否则唯一重试一次。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ClaimApplicationIssueCloseUseCase {

    /** 业务时间统一使用东八区，与数据库时间字段口径一致。 */
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final ClaimApplicationRepository claimApplicationRepository;
    private final ClaimApplicationIssueTransactionService transactionService;

    /**
     * 处理一次问题件关闭请求。
     *
     * @param command 问题件关闭命令
     * @return 关闭结果
     */
    public ClaimApplicationIssueCloseResult close(ClaimApplicationIssueCloseCommand command) {
        ClaimApplication claimApplication = claimApplicationRepository.findByIssueNo(command.issueNo())
                .orElseThrow(() -> new DomainException("问题件不存在，issueNo=" + command.issueNo()));
        if (!claimApplication.closeIssue(command.issueNo(), command.auditor(),
                LocalDateTime.now(BUSINESS_ZONE_ID))) {
            log.info("问题件已关闭，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    claimApplication.getClaimApplicationNo());
            return new ClaimApplicationIssueCloseResult(command.issueNo(), false);
        }
        if (transactionService.save(claimApplication)) {
            log.info("问题件关闭成功，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    claimApplication.getClaimApplicationNo());
            return new ClaimApplicationIssueCloseResult(command.issueNo(), true);
        }
        return resolveAfterConflict(command);
    }

    private ClaimApplicationIssueCloseResult resolveAfterConflict(ClaimApplicationIssueCloseCommand command) {
        ClaimApplication current = claimApplicationRepository.findByIssueNo(command.issueNo())
                .orElseThrow(() -> new DomainException("问题件不存在，issueNo=" + command.issueNo()));
        if (!current.closeIssue(command.issueNo(), command.auditor(), LocalDateTime.now(BUSINESS_ZONE_ID))) {
            log.info("问题件已被并发关闭，按幂等处理，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    current.getClaimApplicationNo());
            return new ClaimApplicationIssueCloseResult(command.issueNo(), false);
        }
        if (transactionService.save(current)) {
            log.info("问题件关闭重试成功，issueNo={}，claimApplicationNo={}", command.issueNo(),
                    current.getClaimApplicationNo());
            return new ClaimApplicationIssueCloseResult(command.issueNo(), true);
        }
        throw new DomainException("申请状态已被并发改变，无法关闭问题件，issueNo=" + command.issueNo());
    }
}
