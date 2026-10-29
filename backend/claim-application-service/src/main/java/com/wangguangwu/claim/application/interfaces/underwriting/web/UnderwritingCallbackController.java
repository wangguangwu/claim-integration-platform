package com.wangguangwu.claim.application.interfaces.underwriting.web;

import com.wangguangwu.claim.application.shared.contract.UnderwritingCompletedCommand;
import com.wangguangwu.claim.application.shared.interfaces.web.ApiResponse;
import com.wangguangwu.claim.application.usecase.underwriting.UnderwritingCallbackUseCase;
import com.wangguangwu.claim.application.usecase.underwriting.result.UnderwritingCallbackResult;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 承保完成回调入口，只做报文与请求头传递，幂等规则由用例保证。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/application/v1/callbacks")
public class UnderwritingCallbackController {

    private final UnderwritingCallbackUseCase underwritingCallbackUseCase;

    /**
     * 接收承保完成通知；重复、迟到或状态不匹配的通知同样返回成功。
     *
     * @param request   回调报文
     * @param requestId 回调发送方生成的请求标识，重试保持不变
     * @param traceId   链路标识
     * @return 统一成功响应
     */
    @PostMapping(value = "/underwriting-completed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<UnderwritingCallbackResult> complete(
            @RequestBody UnderwritingCallbackRequest request,
            @RequestHeader(value = TraceHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = TraceHeaders.TRACE_ID, required = false) String traceId) {
        UnderwritingCallbackResult result = underwritingCallbackUseCase.complete(
                new UnderwritingCompletedCommand(request.claimApplicationNo(), requestId, traceId));
        return ApiResponse.success(traceId, result);
    }
}
