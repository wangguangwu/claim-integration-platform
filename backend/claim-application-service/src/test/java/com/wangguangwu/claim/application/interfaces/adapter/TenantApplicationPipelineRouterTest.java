package com.wangguangwu.claim.application.interfaces.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeException;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 租户申请协议流水线路由测试。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class TenantApplicationPipelineRouterTest {

    /**
     * 没有匹配流水线时应返回明确的协议不支持错误。
     */
    @Test
    void shouldRejectUnsupportedTenantProtocol() {
        TenantApplicationPipelineRouter router = new TenantApplicationPipelineRouter(List.of());

        assertThatThrownBy(() -> router.route(new TenantInfo("tenant-a", "v1"), "{}"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("未找到匹配的租户申请协议流水线");
    }

    /**
     * 多个流水线同时匹配时必须拒绝请求，避免协议映射随机化。
     */
    @Test
    void shouldRejectAmbiguousTenantProtocol() {
        TenantApplicationPipelineRouter router = new TenantApplicationPipelineRouter(
                List.of(pipeline(true, "tenant-a"), pipeline(true, "tenant-a")));

        assertThatThrownBy(() -> router.route(new TenantInfo("tenant-a", "v1"), "{}"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("租户申请协议流水线配置冲突");
    }

    /**
     * 唯一匹配流水线产生与租户上下文一致的命令时应正常返回。
     */
    @Test
    void shouldReturnCommandFromSingleMatchedPipeline() {
        ApplicationIntakeCommand expectedCommand = new ApplicationIntakeCommand("tenant-a", "external-1", null,
                null, null, null, List.of());
        TenantApplicationPipelineRouter router = new TenantApplicationPipelineRouter(
                List.of(pipeline(true, expectedCommand)));

        assertThat(router.route(new TenantInfo("tenant-a", "v1"), "{}")).isSameAs(expectedCommand);
    }

    /**
     * 流水线返回的命令租户编码与上下文不一致时必须拒绝，避免串租户落库。
     */
    @Test
    void shouldRejectCommandWithDifferentTenantCode() {
        ApplicationIntakeCommand foreignCommand = new ApplicationIntakeCommand("tenant-b", "external-1", null,
                null, null, null, List.of());
        TenantApplicationPipelineRouter router = new TenantApplicationPipelineRouter(
                List.of(pipeline(true, foreignCommand)));

        assertThatThrownBy(() -> router.route(new TenantInfo("tenant-a", "v1"), "{}"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("租户申请协议转换结果无效");
    }

    /**
     * 缺少租户编码或协议版本时必须在路由前拒绝。
     */
    @Test
    void shouldRejectMissingTenantContext() {
        TenantApplicationPipelineRouter router = new TenantApplicationPipelineRouter(List.of());

        assertThatThrownBy(() -> router.route(new TenantInfo("tenant-a", " "), "{}"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("缺少租户编码或协议版本");
    }

    /**
     * 构建只返回固定匹配结果与命令的测试流水线。
     *
     * @param supports 是否匹配
     * @param command  固定返回命令，可为 null
     * @return 测试流水线
     */
    private TenantApplicationPipeline pipeline(boolean supports, ApplicationIntakeCommand command) {
        return new TenantApplicationPipeline() {
            @Override
            public boolean supports(TenantInfo tenantInfo) {
                return supports;
            }

            @Override
            public ApplicationIntakeCommand execute(String rawPayload) {
                return command;
            }
        };
    }

    /**
     * 构建返回指定租户编码最小命令的测试流水线。
     *
     * @param supports   是否匹配
     * @param tenantCode 命令中的租户编码
     * @return 测试流水线
     */
    private TenantApplicationPipeline pipeline(boolean supports, String tenantCode) {
        return pipeline(supports, new ApplicationIntakeCommand(tenantCode, "external-1", null, null, null,
                null, List.of()));
    }
}
