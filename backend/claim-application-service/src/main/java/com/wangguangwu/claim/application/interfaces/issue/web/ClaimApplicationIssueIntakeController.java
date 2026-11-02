package com.wangguangwu.claim.application.interfaces.issue.web;

import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.interfaces.intake.web.render.ClaimApplicationIssueResponseRenderer;
import com.wangguangwu.claim.application.interfaces.web.ApiErrorCodeProvider;
import com.wangguangwu.claim.application.shared.contract.BusinessErrorCode;
import com.wangguangwu.claim.application.usecase.issue.ClaimApplicationIssueCloseUseCase;
import com.wangguangwu.claim.application.usecase.issue.ClaimApplicationIssueIntakeUseCase;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueCloseCommand;
import com.wangguangwu.claim.application.usecase.issue.command.ClaimApplicationIssueIntakeCommand;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueCloseResult;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueIntakeResult;
import com.wangguangwu.claim.foundation.observability.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
public class ClaimApplicationIssueIntakeController implements ApiErrorCodeProvider {

    private final ClaimApplicationIssueIntakeUseCase intakeUseCase;
    private final ClaimApplicationIssueCloseUseCase closeUseCase;
    private final ClaimApplicationIssueResponseRenderer responseRenderer;

    /**
     * 下发问题件。
     *
     * @param request 问题件下发请求
     * @param httpRequest 当前 HTTP 请求
     * @return 下发结果
     */
    @PostMapping("/audit-issues")
    public ResponseEntity<Object> receiveIssue(@Valid @RequestBody ClaimApplicationIssueIntakeRequest request,
                                               HttpServletRequest httpRequest) {
        String traceId = httpRequest.getHeader(TraceHeaders.TRACE_ID);
        ClaimApplicationIssueIntakeCommand command = new ClaimApplicationIssueIntakeCommand(
                request.claimApplicationNo(),
                request.issueNo(),
                request.reason(),
                request.auditor(),
                request.issueContent()
        );
        ClaimApplicationIssueIntakeResult result = intakeUseCase.receive(command);
        return responseRenderer.renderIntakeSuccess(traceId, result);
    }

    /**
     * 关闭问题件。
     *
     * @param issueNo 问题件编号
     * @param request 问题件关闭请求
     * @param httpRequest 当前 HTTP 请求
     * @return 关闭结果
     */
    @PostMapping("/audit-issues/{issueNo}/close")
    public ResponseEntity<Object> closeIssue(@PathVariable String issueNo,
                                             @Valid @RequestBody ClaimApplicationIssueCloseRequest request,
                                             HttpServletRequest httpRequest) {
        String traceId = httpRequest.getHeader(TraceHeaders.TRACE_ID);
        ClaimApplicationIssueCloseCommand command = new ClaimApplicationIssueCloseCommand(issueNo, request.auditor());
        ClaimApplicationIssueCloseResult result = closeUseCase.close(command);
        return responseRenderer.renderCloseSuccess(traceId, result);
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
