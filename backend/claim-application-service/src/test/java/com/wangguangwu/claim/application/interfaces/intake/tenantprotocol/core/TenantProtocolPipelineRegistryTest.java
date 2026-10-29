package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wangguangwu.claim.application.usecase.intake.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 租户协议流水线注册中心测试。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class TenantProtocolPipelineRegistryTest {

    /**
     * 没有匹配流水线时应返回明确的协议不支持错误。
     */
    @Test
    void shouldRejectUnsupportedTenantProtocol() {
        TenantProtocolPipelineRegistry registry = new TenantProtocolPipelineRegistry(List.of());

        assertThatThrownBy(() -> registry.getPipeline(new TenantInfo("tenant-a", "v1")))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("未找到匹配的租户申请协议流水线");
    }

    /**
     * 多个流水线同时匹配时必须拒绝请求，避免协议映射随机化。
     */
    @Test
    void shouldRejectAmbiguousTenantProtocol() {
        TenantProtocolPipelineRegistry registry = new TenantProtocolPipelineRegistry(
                List.of(pipeline(true), pipeline(true)));

        assertThatThrownBy(() -> registry.getPipeline(new TenantInfo("tenant-a", "v1")))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("租户申请协议流水线配置冲突");
    }

    /**
     * 唯一匹配流水线应由注册中心原样返回。
     */
    @Test
    void shouldReturnSingleMatchedPipeline() {
        TenantProtocolPipeline expectedPipeline = pipeline(true);
        TenantProtocolPipelineRegistry registry = new TenantProtocolPipelineRegistry(List.of(expectedPipeline));

        assertThat(registry.getPipeline(new TenantInfo("tenant-a", "v1"))).isSameAs(expectedPipeline);
    }

    /**
     * 缺少租户编码或协议版本时必须在路由前拒绝。
     */
    @Test
    void shouldRejectMissingTenantContext() {
        TenantProtocolPipelineRegistry registry = new TenantProtocolPipelineRegistry(List.of());

        assertThatThrownBy(() -> registry.getPipeline(new TenantInfo("tenant-a", " ")))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("缺少租户编码或协议版本");
    }

    /**
     * 构建只返回固定匹配结果的测试流水线。
     *
     * @param supports 是否匹配
     * @return 测试流水线
     */
    private TenantProtocolPipeline pipeline(boolean supports) {
        return new TenantProtocolPipeline() {
            @Override
            public boolean supports(TenantInfo tenantInfo) {
                return supports;
            }

            @Override
            public ApplicationIntakeCommand execute(String rawPayload) {
                return null;
            }
        };
    }
}
