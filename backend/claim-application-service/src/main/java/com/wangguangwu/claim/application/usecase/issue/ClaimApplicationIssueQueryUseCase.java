package com.wangguangwu.claim.application.usecase.issue;

import com.wangguangwu.claim.application.domain.claim.port.ClaimCaseRelationRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.port.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationException;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueQueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 按案件号查询问题件状态的只读用例。
 *
 * <p>先按租户与案件号反查平台申请，再查询该申请未关闭的问题件；不写交互轨迹、不改变任何状态。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationIssueQueryUseCase {

    private final ClaimCaseRelationRepository claimCaseRelationRepository;
    private final ClaimApplicationIssueRepository claimApplicationIssueRepository;

    /**
     * 查询案件对应的申请当前是否有未关闭问题件。
     *
     * @param tenantCode 租户编码
     * @param caseNo     案件号
     * @return 问题件状态
     */
    public ClaimApplicationIssueQueryResult queryByCaseNo(String tenantCode, String caseNo) {
        String claimApplicationNo = claimCaseRelationRepository.findClaimApplicationNo(tenantCode, caseNo)
                .orElseThrow(() -> new ApplicationException(
                        ClaimApplicationIssueErrorCode.CASE_RELATION_NOT_FOUND,
                        "案件关联不存在，tenantCode=" + tenantCode + "，caseNo=" + caseNo));
        return claimApplicationIssueRepository.findOpenByClaimApplicationNo(claimApplicationNo)
                .map(issue -> openIssueResult(claimApplicationNo, caseNo, issue))
                .orElseGet(() -> new ClaimApplicationIssueQueryResult(claimApplicationNo, caseNo, false, null, null,
                        null));
    }

    private ClaimApplicationIssueQueryResult openIssueResult(String claimApplicationNo, String caseNo,
                                                             ClaimApplicationIssue issue) {
        return new ClaimApplicationIssueQueryResult(claimApplicationNo, caseNo, true, issue.getIssueNo(),
                issue.getReason(), issue.getCreatedAt());
    }
}
