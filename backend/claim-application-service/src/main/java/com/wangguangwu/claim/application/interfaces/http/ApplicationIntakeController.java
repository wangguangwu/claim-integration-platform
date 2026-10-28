package com.wangguangwu.claim.application.interfaces.http;

import com.wangguangwu.claim.application.interfaces.adapter.TenantApplicationPipelineRouter;
import com.wangguangwu.claim.application.usecase.ApplicationIntakeUseCase;
import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.result.ApplicationIntakeResult;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import com.wangguangwu.claim.foundation.tenant.context.TenantContext;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
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
@RestController
@RequestMapping("/api/application/v1/applications")
public class ApplicationIntakeController {

    private final TenantApplicationPipelineRouter pipelineRouter;
    private final ApplicationIntakeUseCase applicationIntakeUseCase;
    private final TenantApplicationResponseRouter responseRouter;

    public ApplicationIntakeController(TenantApplicationPipelineRouter pipelineRouter,
                                       ApplicationIntakeUseCase applicationIntakeUseCase,
                                       TenantApplicationResponseRouter responseRouter) {
        this.pipelineRouter = pipelineRouter;
        this.applicationIntakeUseCase = applicationIntakeUseCase;
        this.responseRouter = responseRouter;
    }

    /**
     * 接收租户私有报文，经协议流水线转换后执行标准化签收，并按租户渲染响应结构。
     *
     * @param rawPayload 租户私有请求正文
     * @param request    当前 HTTP 请求
     * @return 租户协议响应
     */
    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Object> intake(@RequestBody String rawPayload, HttpServletRequest request) {
        TenantInfo tenantInfo = TenantContext.get();
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        ApplicationIntakeCommand command = pipelineRouter.route(tenantInfo, rawPayload);
        ApplicationIntakeResult result = applicationIntakeUseCase.intake(command,
                request.getHeader(TraceHeaders.REQUEST_ID), traceId);
        return responseRouter.renderSuccess(tenantInfo, traceId, result);
    }
}
