package com.wangguangwu.claim.application.interfaces.intake.web.render;

import com.wangguangwu.claim.application.domain.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.interfaces.intake.web.issue.ClaimApplicationIssueCloseResponse;
import com.wangguangwu.claim.application.interfaces.intake.web.issue.ClaimApplicationIssueIntakeResponse;
import com.wangguangwu.claim.application.shared.interfaces.web.ApiResponse;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueCloseResult;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueIntakeResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 问题件链路统一响应渲染器，全部租户共用同一种响应结构与状态码映射。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Component
public class ClaimApplicationIssueResponseRenderer {

    /**
     * 渲染问题件下发成功响应。
     *
     * @param traceId 链路标识
     * @param result  下发结果
     * @return HTTP 响应
     */
    public ResponseEntity<Object> renderIntakeSuccess(String traceId, ClaimApplicationIssueIntakeResult result) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.success(traceId, new ClaimApplicationIssueIntakeResponse(
                        result.issueNo(),
                        result.claimApplicationNo(),
                        result.created()
                )));
    }

    /**
     * 渲染问题件关闭成功响应。
     *
     * @param traceId 链路标识
     * @param result  关闭结果
     * @return HTTP 响应
     */
    public ResponseEntity<Object> renderCloseSuccess(String traceId, ClaimApplicationIssueCloseResult result) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.success(traceId, new ClaimApplicationIssueCloseResponse(
                        result.issueNo(),
                        result.closed()
                )));
    }

    /**
     * 渲染问题件失败响应，按错误码映射 HTTP 状态。
     *
     * @param traceId      链路标识
     * @param errorCode    业务错误码
     * @param errorMessage 面向调用方的错误说明
     * @return HTTP 响应
     */
    public ResponseEntity<Object> renderFailure(String traceId, ClaimApplicationIssueErrorCode errorCode,
                                                String errorMessage) {
        return ResponseEntity.status(statusOf(errorCode))
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(errorCode.name(), errorMessage, traceId));
    }

    private HttpStatus statusOf(ClaimApplicationIssueErrorCode errorCode) {
        return switch (errorCode) {
            case ISSUE_APPLICATION_NOT_FOUND, ISSUE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ISSUE_APPLICATION_STATUS_INVALID, ISSUE_INTAKE_INVALID -> HttpStatus.BAD_REQUEST;
            case ISSUE_ALREADY_OPEN -> HttpStatus.CONFLICT;
            case ISSUE_SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
