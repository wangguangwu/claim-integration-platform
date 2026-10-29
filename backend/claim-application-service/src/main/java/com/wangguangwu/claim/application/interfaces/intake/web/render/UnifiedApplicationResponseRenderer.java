package com.wangguangwu.claim.application.interfaces.intake.web.render;

import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.interfaces.intake.web.ApplicationIntakeResponse;
import com.wangguangwu.claim.application.shared.interfaces.web.ApiResponse;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 平台统一响应结构渲染器，作为未定制响应协议的租户兜底实现。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class UnifiedApplicationResponseRenderer implements TenantApplicationResponseRenderer {

    /**
     * 统一结构覆盖全部租户，由响应路由作为兜底选择。
     *
     * @param tenantInfo 当前租户信息
     * @return 恒为 true
     */
    @Override
    public boolean supports(TenantInfo tenantInfo) {
        return true;
    }

    @Override
    public ResponseEntity<Object> renderSuccess(TenantInfo tenantInfo, String traceId, ClaimApplicationIntakeResult result) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.success(traceId, new ApplicationIntakeResponse(result.applicationNo())));
    }

    @Override
    public ResponseEntity<Object> renderFailure(TenantInfo tenantInfo, String traceId,
                                                ApplicationIntakeErrorCode errorCode, String errorMessage) {
        return ResponseEntity.status(statusOf(errorCode))
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(errorCode.name(), errorMessage, traceId));
    }

    private HttpStatus statusOf(ApplicationIntakeErrorCode errorCode) {
        return switch (errorCode) {
            case APPLICATION_INTAKE_BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
            case APPLICATION_INTAKE_SYSTEM_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case TENANT_PROTOCOL_UNSUPPORTED, TENANT_PROTOCOL_AMBIGUOUS,
                 APPLICATION_INTAKE_INVALID -> HttpStatus.BAD_REQUEST;
        };
    }
}
