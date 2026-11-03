package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.domain.claimcase.port.ClaimCaseRelationRepository;
import com.wangguangwu.claim.application.application.exception.ApplicationException;
import com.wangguangwu.claim.application.application.query.model.IssueView;
import com.wangguangwu.claim.application.application.port.ClaimApplicationDetailQuery;
import com.wangguangwu.claim.foundation.observability.flow.FlowLog;
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
    private final ClaimApplicationDetailQuery claimApplicationDetailQuery;

    /**
     * 查询案件对应的申请当前是否有未关闭问题件。
     *
     * @param tenantCode 租户编码
     * @param caseNo     案件号
     * @return 问题件状态
     */
    @FlowLog("问题件状态查询")
    public ClaimApplicationIssueQueryResult queryByCaseNo(String tenantCode, String caseNo) {
        String claimApplicationNo = claimCaseRelationRepository.findClaimApplicationNo(tenantCode, caseNo)
                .orElseThrow(() -> new ApplicationException("案件关联不存在，tenantCode=" + tenantCode + "，caseNo=" + caseNo));
        return claimApplicationDetailQuery.findOpenIssue(tenantCode, claimApplicationNo)
                .map(issue -> openIssueResult(claimApplicationNo, caseNo, issue))
                .orElseGet(() -> new ClaimApplicationIssueQueryResult(claimApplicationNo, caseNo, false, null, null,
                        null));
    }

    private ClaimApplicationIssueQueryResult openIssueResult(String claimApplicationNo, String caseNo,
                                                             IssueView issue) {
        return new ClaimApplicationIssueQueryResult(claimApplicationNo, caseNo, true, issue.issueNo(),
                issue.reason(), issue.createdAt());
    }
}
