package com.wangguangwu.claim.application.interfaces.intake.web.render;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.interfaces.intake.web.ClaimApplicationIntakeResponse;
import com.wangguangwu.claim.application.shared.interfaces.web.ApiResponse;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 平台统一申请响应渲染器，全部租户共用同一种响应结构与状态码映射。
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
@Component
public class ClaimApplicationResponseRenderer {

    /**
     * 渲染签收成功响应。
     *
     * @param traceId 链路标识
     * @param result  签收结果
     * @return HTTP 响应
     */
    public ResponseEntity<Object> renderSuccess(String traceId, ClaimApplicationIntakeResult result) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.success(traceId, new ClaimApplicationIntakeResponse(result.claimApplicationNo())));
    }

    /**
     * 渲染签收失败响应，按错误码映射 HTTP 状态。
     *
     * @param traceId      链路标识
     * @param errorCode    业务错误码
     * @param errorMessage 面向调用方的错误说明
     * @return HTTP 响应
     */
    public ResponseEntity<Object> renderFailure(String traceId, ClaimApplicationIntakeErrorCode errorCode,
                                                String errorMessage) {
        return ResponseEntity.status(statusOf(errorCode))
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(errorCode.name(), errorMessage, traceId));
    }

    private HttpStatus statusOf(ClaimApplicationIntakeErrorCode errorCode) {
        return switch (errorCode) {
            case CLAIM_APPLICATION_INTAKE_BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
            case CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case TENANT_PROTOCOL_UNSUPPORTED, TENANT_PROTOCOL_AMBIGUOUS,
                 CLAIM_APPLICATION_INTAKE_INVALID -> HttpStatus.BAD_REQUEST;
        };
    }
}
