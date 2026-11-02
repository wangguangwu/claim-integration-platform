package com.wangguangwu.claim.application.interfaces.intake.web.render;

import com.wangguangwu.claim.application.interfaces.issue.web.ClaimApplicationIssueCloseResponse;
import com.wangguangwu.claim.application.interfaces.issue.web.ClaimApplicationIssueIntakeResponse;
import com.wangguangwu.claim.application.interfaces.issue.web.ClaimApplicationIssueQueryResponse;
import com.wangguangwu.claim.application.interfaces.web.ApiResponse;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueCloseResult;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueIntakeResult;
import com.wangguangwu.claim.application.usecase.issue.result.ClaimApplicationIssueQueryResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 问题件链路统一响应渲染器，全部租户共用同一种成功响应结构。
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
     * 渲染问题件状态查询成功响应。
     *
     * @param traceId 链路标识
     * @param result  查询结果
     * @return HTTP 响应
     */
    public ResponseEntity<Object> renderQuerySuccess(String traceId, ClaimApplicationIssueQueryResult result) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.success(traceId, new ClaimApplicationIssueQueryResponse(
                        result.claimApplicationNo(),
                        result.caseNo(),
                        result.hasOpenIssue(),
                        result.issueNo(),
                        result.reason(),
                        result.raisedAt()
                )));
    }
}
