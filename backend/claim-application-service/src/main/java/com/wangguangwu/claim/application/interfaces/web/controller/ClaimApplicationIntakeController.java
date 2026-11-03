package com.wangguangwu.claim.application.interfaces.web.controller;

import com.wangguangwu.claim.application.interfaces.web.dto.response.ApiResponse;
import com.wangguangwu.claim.application.interfaces.web.dto.response.ClaimApplicationIntakeResponse;
import com.wangguangwu.claim.application.interfaces.web.tenantprotocol.pipeline.TenantProtocolPipelineRegistry;
import com.wangguangwu.claim.application.application.process.ClaimApplicationIntakeUseCase;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.application.process.ClaimApplicationIntakeResult;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import com.wangguangwu.claim.foundation.tenant.context.TenantContext;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 理赔申请统一接入 HTTP 入口，所有租户共用，按请求头选择租户协议流水线。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/claim/v1/application")
public class ClaimApplicationIntakeController {

    private final TenantProtocolPipelineRegistry pipelineRegistry;
    private final ClaimApplicationIntakeUseCase claimApplicationIntakeUseCase;

    /**
     * 接收租户私有报文，经协议流水线归一化为受理命令后执行标准化签收。
     *
     * @param rawPayload 租户私有请求正文
     * @param request    当前 HTTP 请求
     * @return 统一成功响应
     */
    @PostMapping(value = "/intake", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ClaimApplicationIntakeResponse> intake(@RequestBody String rawPayload,
                                                              HttpServletRequest request) {
        TenantInfo tenantInfo = TenantContext.get();
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        ClaimApplicationIntakeCommand command = pipelineRegistry.execute(tenantInfo, rawPayload);
        ClaimApplicationIntakeResult result = claimApplicationIntakeUseCase.intake(command,
                request.getHeader(TraceHeaders.REQUEST_ID), traceId);
        return ApiResponse.success(traceId, new ClaimApplicationIntakeResponse(result.claimApplicationNo()));
    }
}
