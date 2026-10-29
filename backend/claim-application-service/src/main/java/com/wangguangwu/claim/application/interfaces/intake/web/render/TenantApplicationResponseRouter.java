package com.wangguangwu.claim.application.interfaces.intake.web.render;

import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 按租户选择唯一的响应渲染器，未定制协议的租户使用统一结构兜底。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class TenantApplicationResponseRouter {

    private final List<TenantApplicationResponseRenderer> tenantRenderers;
    private final UnifiedApplicationResponseRenderer unifiedRenderer;

    public TenantApplicationResponseRouter(List<TenantApplicationResponseRenderer> renderers,
                                           UnifiedApplicationResponseRenderer unifiedRenderer) {
        this.tenantRenderers = renderers.stream()
                .filter(renderer -> !(renderer instanceof UnifiedApplicationResponseRenderer))
                .toList();
        this.unifiedRenderer = unifiedRenderer;
    }

    /**
     * 渲染签收成功响应。
     *
     * @param tenantInfo 当前租户信息
     * @param traceId    链路标识
     * @param result     签收结果
     * @return HTTP 响应
     */
    public ResponseEntity<Object> renderSuccess(TenantInfo tenantInfo, String traceId,
                                                ClaimApplicationIntakeResult result) {
        return rendererFor(tenantInfo).renderSuccess(tenantInfo, traceId, result);
    }

    /**
     * 渲染签收失败响应。
     *
     * @param tenantInfo   当前租户信息
     * @param traceId      链路标识
     * @param errorCode    业务错误码
     * @param errorMessage 面向调用方的错误说明
     * @return HTTP 响应
     */
    public ResponseEntity<Object> renderFailure(TenantInfo tenantInfo, String traceId,
                                                ApplicationIntakeErrorCode errorCode, String errorMessage) {
        return rendererFor(tenantInfo).renderFailure(tenantInfo, traceId, errorCode, errorMessage);
    }

    private TenantApplicationResponseRenderer rendererFor(TenantInfo tenantInfo) {
        if (tenantInfo == null) {
            return unifiedRenderer;
        }
        List<TenantApplicationResponseRenderer> matchedRenderers = tenantRenderers.stream()
                .filter(renderer -> renderer.supports(tenantInfo))
                .toList();
        if (matchedRenderers.isEmpty()) {
            return unifiedRenderer;
        }
        if (matchedRenderers.size() > 1) {
            throw new ApplicationIntakeException(ApplicationIntakeErrorCode.TENANT_PROTOCOL_AMBIGUOUS,
                    "租户申请响应渲染器配置冲突");
        }
        return matchedRenderers.getFirst();
    }
}
