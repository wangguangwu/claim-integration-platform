package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.wangguangwu.claim.application.usecase.intake.command.ApplicationIntakeCommand;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 租户协议流水线模板测试。
 *
 * @author wangguangwu
 * @since 2026-10-28
 */
class AbstractTenantProtocolPipelineTest {

    /**
     * 模板必须按固定顺序执行全部协议节点，避免租户实现绕过验签或解密。
     */
    @Test
    void shouldExecuteProtocolStagesInFixedOrder() {
        List<String> stages = new ArrayList<>();
        ApplicationIntakeCommand expectedCommand = new ApplicationIntakeCommand("tenant-a", "external-1", null,
                null, null, null, List.of());
        AbstractTenantProtocolPipeline<String> pipeline = new AbstractTenantProtocolPipeline<>("tenant-a", "v1",
                rawPayload -> {
                    stages.add("解析");
                    return rawPayload;
                }, request -> stages.add("校验"), (tenantCode, request) -> {
                    stages.add("转换");
                    return expectedCommand;
                }) {
            @Override
            protected void verifySignature(String rawPayload) {
                stages.add("验签");
            }

            @Override
            protected String decrypt(String rawPayload) {
                stages.add("解密");
                return rawPayload;
            }
        };

        assertThat(pipeline.execute("{} ")).isSameAs(expectedCommand);
        assertThat(stages).containsExactly("验签", "解密", "解析", "校验", "转换");
    }
}
