package com.wangguangwu.claim.application.interfaces.intake.web;

import com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.pipeline.TenantProtocolPipelineFactory;
import com.wangguangwu.claim.application.interfaces.intake.web.render.ClaimApplicationResponseRenderer;
import com.wangguangwu.claim.application.usecase.intake.ClaimApplicationIntakeUseCase;
import com.wangguangwu.claim.application.usecase.intake.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import com.wangguangwu.claim.foundation.tenant.context.TenantContext;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 理赔申请统一接入 HTTP 入口，只负责上下文传递与结果渲染。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/claim/v1/application")
public class ClaimApplicationIntakeController {

    private final TenantProtocolPipelineFactory pipelineFactory;
    private final ClaimApplicationIntakeUseCase claimApplicationIntakeUseCase;
    private final ClaimApplicationResponseRenderer responseRenderer;

    /**
     * 接收租户私有报文，经协议流水线归一化为受理命令后执行标准化签收，并渲染统一响应结构。
     *
     * @param rawPayload 租户私有请求正文
     * @param request    当前 HTTP 请求
     * @return 租户协议响应
     */
    @PostMapping("/intake")
    public ResponseEntity<Object> intake(@RequestBody String rawPayload, HttpServletRequest request) {
        TenantInfo tenantInfo = TenantContext.get();
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        ClaimApplicationIntakeCommand command = pipelineFactory.execute(tenantInfo, rawPayload);
        ClaimApplicationIntakeResult result = claimApplicationIntakeUseCase.intake(command,
                request.getHeader(TraceHeaders.REQUEST_ID), traceId);
        return responseRenderer.renderSuccess(traceId, result);
    }
}
