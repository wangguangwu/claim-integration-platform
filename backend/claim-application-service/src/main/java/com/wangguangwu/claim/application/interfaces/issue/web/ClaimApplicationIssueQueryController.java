package com.wangguangwu.claim.application.interfaces.issue.web;

import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.interfaces.issue.web.render.ClaimApplicationIssueResponseRenderer;
import com.wangguangwu.claim.application.interfaces.web.ApiErrorCodeProvider;
import com.wangguangwu.claim.application.shared.contract.BusinessErrorCode;
import com.wangguangwu.claim.application.shared.contract.BusinessException;
import com.wangguangwu.claim.application.usecase.issue.ClaimApplicationIssueQueryUseCase;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import com.wangguangwu.claim.foundation.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
public class ClaimApplicationIssueQueryController implements ApiErrorCodeProvider {

    private static final String MISSING_TENANT_MESSAGE = "缺少租户上下文";

    private final ClaimApplicationIssueQueryUseCase queryUseCase;
    private final ClaimApplicationIssueResponseRenderer responseRenderer;

    /**
     * 查询案件对应的申请是否存在未关闭问题件。
     *
     * @param caseNo      案件号
     * @param httpRequest 当前 HTTP 请求
     * @return 问题件状态
     */
    @GetMapping("/{caseNo}/issue")
    public ResponseEntity<Object> queryIssue(@PathVariable String caseNo, HttpServletRequest httpRequest) {
        String traceId = httpRequest.getHeader(TraceHeaders.TRACE_ID);
        String tenantCode = TenantContext.tenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new BusinessException(ClaimApplicationIssueErrorCode.ISSUE_INTAKE_INVALID, MISSING_TENANT_MESSAGE);
        }
        return responseRenderer.renderQuerySuccess(traceId, queryUseCase.queryByCaseNo(tenantCode, caseNo));
    }

    @Override
    public BusinessErrorCode requestInvalidCode() {
        return ClaimApplicationIssueErrorCode.ISSUE_INTAKE_INVALID;
    }

    @Override
    public BusinessErrorCode systemErrorCode() {
        return ClaimApplicationIssueErrorCode.ISSUE_SYSTEM_ERROR;
    }
}
