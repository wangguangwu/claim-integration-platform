package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.usecase.intake.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 租户协议流水线工厂测试。
 *
 * @author wangguangwu
 * @since 2026-10-28
 */
class TenantProtocolPipelineFactoryTest {

    /**
     * 工厂执行后应返回当前租户的受理命令。
     */
    @Test
    void shouldExecuteMatchedPipeline() {
        ApplicationIntakeCommand expectedCommand = command("tenant-a");
        TenantProtocolPipelineFactory factory = factory(pipeline(expectedCommand));

        assertThat(factory.execute(new TenantInfo("tenant-a", "v1"), "{}"))
                .isSameAs(expectedCommand);
    }

    /**
     * 转换结果中的租户编码不一致时必须拒绝，避免跨租户写入。
     */
    @Test
    void shouldRejectApplicationWithDifferentTenantCode() {
        TenantProtocolPipelineFactory factory = factory(pipeline(command("tenant-b")));

        assertThatThrownBy(() -> factory.execute(new TenantInfo("tenant-a", "v1"), "{}"))
                .isInstanceOf(ApplicationIntakeException.class)
                .hasMessage("租户申请协议转换结果无效");
    }

    private TenantProtocolPipelineFactory factory(TenantProtocolPipeline pipeline) {
        return new TenantProtocolPipelineFactory(new TenantProtocolPipelineRegistry(List.of(pipeline)));
    }

    private TenantProtocolPipeline pipeline(ApplicationIntakeCommand command) {
        return new TenantProtocolPipeline() {
            @Override
            public boolean supports(TenantInfo tenantInfo) {
                return true;
            }

            @Override
            public ApplicationIntakeCommand execute(String rawPayload) {
                return command;
            }
        };
    }

    private ApplicationIntakeCommand command(String tenantCode) {
        return new ApplicationIntakeCommand(tenantCode, "external-1", null, null, null, null, List.of());
    }
}
