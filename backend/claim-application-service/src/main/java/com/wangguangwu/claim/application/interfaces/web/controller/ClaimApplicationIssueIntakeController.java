package com.wangguangwu.claim.application.interfaces.web.controller;

import com.wangguangwu.claim.application.interfaces.web.dto.request.ClaimApplicationIssueCloseRequest;
import com.wangguangwu.claim.application.interfaces.web.dto.request.ClaimApplicationIssueIntakeRequest;
import com.wangguangwu.claim.application.interfaces.web.dto.response.ApiResponse;
import com.wangguangwu.claim.application.interfaces.web.dto.response.ClaimApplicationIssueCloseResponse;
import com.wangguangwu.claim.application.interfaces.web.dto.response.ClaimApplicationIssueIntakeResponse;
import com.wangguangwu.claim.application.application.process.ClaimApplicationIssueCloseUseCase;
import com.wangguangwu.claim.application.application.process.ClaimApplicationIssueIntakeUseCase;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIssueCloseCommand;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIssueIntakeCommand;
import com.wangguangwu.claim.application.application.process.ClaimApplicationIssueCloseResult;
import com.wangguangwu.claim.application.application.process.ClaimApplicationIssueIntakeResult;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问题件回调 HTTP 入口，接收审核系统的问题件下发与关闭请求。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/claim/v1/callbacks")
public class ClaimApplicationIssueIntakeController {

    private final ClaimApplicationIssueIntakeUseCase intakeUseCase;
    private final ClaimApplicationIssueCloseUseCase closeUseCase;

    /**
     * 下发问题件。
     *
     * @param request 问题件下发请求
     * @param httpRequest 当前 HTTP 请求
     * @return 统一成功响应
     */
    @PostMapping(value = "/audit-issues", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ClaimApplicationIssueIntakeResponse> receiveIssue(
            @Valid @RequestBody ClaimApplicationIssueIntakeRequest request, HttpServletRequest httpRequest) {
        String traceId = httpRequest.getHeader(TraceHeaders.TRACE_ID);
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                request.claimApplicationNo(),
                request.issueNo(),
                request.reason(),
                request.auditor(),
                request.issueContent()
        );
        ClaimApplicationIssueIntakeResult result = intakeUseCase.receive(command);
        return ApiResponse.success(traceId, new ClaimApplicationIssueIntakeResponse(
                result.issueNo(),
                result.claimApplicationNo(),
                result.created()
        ));
    }

    /**
     * 关闭问题件。
     *
     * @param issueNo 问题件编号
     * @param request 问题件关闭请求
     * @param httpRequest 当前 HTTP 请求
     * @return 统一成功响应
     */
    @PostMapping(value = "/audit-issues/{issueNo}/close", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ClaimApplicationIssueCloseResponse> closeIssue(
            @PathVariable String issueNo, @Valid @RequestBody ClaimApplicationIssueCloseRequest request,
            HttpServletRequest httpRequest) {
        String traceId = httpRequest.getHeader(TraceHeaders.TRACE_ID);
        ClaimApplicationIssueCloseCommand command = new ClaimApplicationIssueCloseCommand(issueNo, request.auditor());
        ClaimApplicationIssueCloseResult result = closeUseCase.close(command);
        return ApiResponse.success(traceId, new ClaimApplicationIssueCloseResponse(result.issueNo(), result.closed()));
    }
}
