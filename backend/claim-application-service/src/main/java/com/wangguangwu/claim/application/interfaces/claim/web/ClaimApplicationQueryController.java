package com.wangguangwu.claim.application.interfaces.claim.web;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationQueryErrorCode;
import com.wangguangwu.claim.application.interfaces.web.ApiErrorCodeProvider;
import com.wangguangwu.claim.application.interfaces.web.ApiResponse;
import com.wangguangwu.claim.application.shared.contract.BusinessErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationException;
import com.wangguangwu.claim.application.usecase.query.ClaimApplicationDetailQueryUseCase;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithCases;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithIssues;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithMaterials;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import com.wangguangwu.claim.foundation.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 理赔申请详情查询入口，按当前租户与申请编号返回读模型。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/claim/v1/application")
public class ClaimApplicationQueryController implements ApiErrorCodeProvider {

    private static final String MISSING_TENANT_MESSAGE = "缺少租户上下文";

    private final ClaimApplicationDetailQueryUseCase queryUseCase;

    /**
     * 查询申请摘要与未删除材料。
     *
     * @param claimApplicationNo 平台申请编号
     * @param httpRequest        当前 HTTP 请求
     * @return 申请摘要与材料
     */
    @GetMapping("/{claimApplicationNo}/materials")
    public ApiResponse<ClaimApplicationWithMaterials> queryMaterials(@PathVariable String claimApplicationNo,
                                                                     HttpServletRequest httpRequest) {
        return ApiResponse.success(traceId(httpRequest),
                queryUseCase.findWithMaterials(currentTenantCode(), claimApplicationNo));
    }

    /**
     * 查询申请摘要与全部问题件。
     *
     * @param claimApplicationNo 平台申请编号
     * @param httpRequest        当前 HTTP 请求
     * @return 申请摘要与问题件
     */
    @GetMapping("/{claimApplicationNo}/issues")
    public ApiResponse<ClaimApplicationWithIssues> queryIssues(@PathVariable String claimApplicationNo,
                                                               HttpServletRequest httpRequest) {
        return ApiResponse.success(traceId(httpRequest),
                queryUseCase.findWithIssues(currentTenantCode(), claimApplicationNo));
    }

    /**
     * 查询申请摘要与案件关联。
     *
     * @param claimApplicationNo 平台申请编号
     * @param httpRequest        当前 HTTP 请求
     * @return 申请摘要与案件关联
     */
    @GetMapping("/{claimApplicationNo}/cases")
    public ApiResponse<ClaimApplicationWithCases> queryCases(@PathVariable String claimApplicationNo,
                                                             HttpServletRequest httpRequest) {
        return ApiResponse.success(traceId(httpRequest),
                queryUseCase.findWithCases(currentTenantCode(), claimApplicationNo));
    }

    @Override
    public BusinessErrorCode requestInvalidCode() {
        return ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_INVALID;
    }

    @Override
    public BusinessErrorCode systemErrorCode() {
        return ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_SYSTEM_ERROR;
    }

    private String currentTenantCode() {
        String tenantCode = TenantContext.tenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new ApplicationException(ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_INVALID,
                    MISSING_TENANT_MESSAGE);
        }
        return tenantCode;
    }

    private String traceId(HttpServletRequest httpRequest) {
        return httpRequest.getHeader(TraceHeaders.TRACE_ID);
    }
}
