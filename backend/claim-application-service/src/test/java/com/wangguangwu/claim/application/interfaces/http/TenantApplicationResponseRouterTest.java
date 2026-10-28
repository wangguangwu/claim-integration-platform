package com.wangguangwu.claim.application.interfaces.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.usecase.result.ApplicationIntakeDisposition;
import com.wangguangwu.claim.application.usecase.result.ApplicationIntakeResult;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 租户响应渲染路由测试。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class TenantApplicationResponseRouterTest {

    private static final TenantInfo TENANT_INFO = new TenantInfo("demo-insurer-a", "v1");
    private static final ApplicationIntakeResult INTAKE_RESULT = new ApplicationIntakeResult(
            "A2026102700000001", ApplicationIntakeDisposition.CREATED);

    private final UnifiedApplicationResponseRenderer unifiedRenderer = new UnifiedApplicationResponseRenderer();

    /**
     * 未定制响应协议的租户必须使用平台统一结构。
     */
    @Test
    void shouldRenderUnifiedStructureWhenNoTenantRendererMatched() {
        TenantApplicationResponseRenderer unmatchedRenderer = mockTenantRenderer(false);
        TenantApplicationResponseRouter router = new TenantApplicationResponseRouter(
                List.of(unifiedRenderer, unmatchedRenderer), unifiedRenderer);

        ResponseEntity<Object> response = router.renderSuccess(TENANT_INFO, "trace-1", INTAKE_RESULT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(ApiResponse.success("trace-1",
                new ApplicationIntakeResponse("A2026102700000001")));
    }

    /**
     * 租户定制渲染器存在时必须优先返回租户协议结构。
     */
    @Test
    void shouldRenderTenantStructureWhenRendererMatched() {
        TenantApplicationResponseRenderer tenantRenderer = mockTenantRenderer(true);
        when(tenantRenderer.renderSuccess(any(), anyString(), any()))
                .thenReturn(ResponseEntity.ok().body(Map.of("retCode", "0")));
        TenantApplicationResponseRouter router = new TenantApplicationResponseRouter(
                List.of(unifiedRenderer, tenantRenderer), unifiedRenderer);

        ResponseEntity<Object> response = router.renderSuccess(TENANT_INFO, "trace-1", INTAKE_RESULT);

        assertThat(response.getBody()).isEqualTo(Map.of("retCode", "0"));
    }

    /**
     * 缺少租户上下文时只能使用统一结构，避免渲染阶段再次失败。
     */
    @Test
    void shouldRenderUnifiedStructureWhenTenantInfoMissing() {
        TenantApplicationResponseRenderer tenantRenderer = mockTenantRenderer(true);
        TenantApplicationResponseRouter router = new TenantApplicationResponseRouter(
                List.of(unifiedRenderer, tenantRenderer), unifiedRenderer);

        ResponseEntity<Object> response = router.renderSuccess(null, "trace-1", INTAKE_RESULT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * 签收锁等待超时属于可重试的临时不可用，统一结构返回 503。
     */
    @Test
    void shouldReturnServiceUnavailableWhenIntakeBusy() {
        TenantApplicationResponseRouter router = new TenantApplicationResponseRouter(
                List.of(unifiedRenderer), unifiedRenderer);

        ResponseEntity<Object> response = router.renderFailure(TENANT_INFO, "trace-1",
                ApplicationIntakeErrorCode.APPLICATION_INTAKE_BUSY, "请稍后重试");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(ApiResponse.failure("APPLICATION_INTAKE_BUSY", "请稍后重试",
                "trace-1"));
    }

    /**
     * 同一租户匹配多个渲染器属于配置冲突，必须显式暴露。
     */
    @Test
    void shouldRejectAmbiguousTenantRenderers() {
        TenantApplicationResponseRouter router = new TenantApplicationResponseRouter(
                List.of(unifiedRenderer, mockTenantRenderer(true), mockTenantRenderer(true)), unifiedRenderer);

        assertThatThrownBy(() -> router.renderSuccess(TENANT_INFO, "trace-1", INTAKE_RESULT))
                .isInstanceOf(ApplicationIntakeException.class)
                .satisfies(exception -> assertThat(((ApplicationIntakeException) exception).getErrorCode())
                        .isEqualTo(ApplicationIntakeErrorCode.TENANT_PROTOCOL_AMBIGUOUS));
    }

    private TenantApplicationResponseRenderer mockTenantRenderer(boolean supports) {
        TenantApplicationResponseRenderer renderer = mock(TenantApplicationResponseRenderer.class);
        when(renderer.supports(any())).thenReturn(supports);
        return renderer;
    }
}
