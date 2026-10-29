package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core;

import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
import com.wangguangwu.claim.application.usecase.intake.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import org.springframework.stereotype.Component;

/**
 * 租户协议流水线工厂，根据租户上下文获取对应的租户协议流水线，执行协议适配并返回受理命令。
 *
 * @author wangguangwu
 * @since 2026-10-28
 */
@Component
public class TenantProtocolPipelineFactory {

    private final TenantProtocolPipelineRegistry pipelineRegistry;

    public TenantProtocolPipelineFactory(TenantProtocolPipelineRegistry pipelineRegistry) {
        this.pipelineRegistry = pipelineRegistry;
    }

    /**
     * 根据租户编码和协议版本获取对应流水线。
     *
     * @param tenantInfo 当前租户上下文
     * @return 唯一匹配的租户协议流水线
     */
    public TenantProtocolPipeline getPipeline(TenantInfo tenantInfo) {
        return pipelineRegistry.getPipeline(tenantInfo);
    }

    /**
     * 执行租户协议适配并校验转换结果未跨租户。
     *
     * @param tenantInfo 当前租户上下文
     * @param rawPayload 租户原始请求正文
     * @return 签收用例的标准命令
     */
    public ApplicationIntakeCommand execute(TenantInfo tenantInfo, String rawPayload) {
        ApplicationIntakeCommand command = getPipeline(tenantInfo).execute(rawPayload);
        if (command == null || !tenantInfo.tenantCode().equals(command.tenantCode())) {
            throw ApplicationIntakeException.invalid("租户申请协议转换结果无效");
        }
        return command;
    }
}
