package com.wangguangwu.claim.application.interfaces.web.controller;

import com.wangguangwu.claim.application.interfaces.web.dto.response.ApiResponse;
import com.wangguangwu.claim.application.interfaces.web.dto.response.ClaimApplicationIssueQueryResponse;
import com.wangguangwu.claim.application.shared.exception.BusinessException;
import com.wangguangwu.claim.application.application.process.ClaimApplicationIssueQueryResult;
import com.wangguangwu.claim.application.application.process.ClaimApplicationIssueQueryUseCase;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import com.wangguangwu.claim.foundation.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问题件状态查询入口，按案件号返回申请当前是否有未关闭问题件。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/claim/v1/cases")
public class ClaimApplicationIssueQueryController {

    private static final String MISSING_TENANT_MESSAGE = "缺少租户上下文";

    private final ClaimApplicationIssueQueryUseCase queryUseCase;

    /**
     * 查询案件对应的申请是否存在未关闭问题件。
     *
     * @param caseNo      案件号
     * @param httpRequest 当前 HTTP 请求
     * @return 统一成功响应
     */
    @GetMapping(value = "/{caseNo}/issue", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ClaimApplicationIssueQueryResponse> queryIssue(@PathVariable String caseNo,
                                                                      HttpServletRequest httpRequest) {
        String traceId = httpRequest.getHeader(TraceHeaders.TRACE_ID);
        String tenantCode = TenantContext.tenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new BusinessException(MISSING_TENANT_MESSAGE);
        }
        ClaimApplicationIssueQueryResult result = queryUseCase.queryByCaseNo(tenantCode, caseNo);
        return ApiResponse.success(traceId, new ClaimApplicationIssueQueryResponse(
                result.claimApplicationNo(),
                result.caseNo(),
                result.hasOpenIssue(),
                result.issueNo(),
                result.reason(),
                result.raisedAt()
        ));
    }
}
