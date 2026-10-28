package com.wangguangwu.claim.application.interfaces.adapter;

import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationIntakeException;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 按租户编码和协议版本路由唯一申请协议流水线。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class TenantApplicationPipelineRouter {

    private final List<TenantApplicationPipeline> pipelines;

    public TenantApplicationPipelineRouter(List<TenantApplicationPipeline> pipelines) {
        this.pipelines = pipelines;
    }

    /**
     * 选择唯一流水线并把租户私有报文转换为标准命令。
     *
     * @param tenantInfo 当前租户信息
     * @param rawPayload 原始请求正文
     * @return 标准化签收命令
     */
    public ApplicationIntakeCommand route(TenantInfo tenantInfo, String rawPayload) {
        if (tenantInfo == null || !StringUtils.hasText(tenantInfo.tenantCode())
                || !StringUtils.hasText(tenantInfo.protocolVersion())) {
            throw new ApplicationIntakeException(ApplicationIntakeErrorCode.APPLICATION_INTAKE_INVALID,
                    "缺少租户编码或协议版本");
        }
        List<TenantApplicationPipeline> matchedPipelines = pipelines.stream()
                .filter(pipeline -> pipeline.supports(tenantInfo))
                .toList();
        if (matchedPipelines.isEmpty()) {
            throw new ApplicationIntakeException(ApplicationIntakeErrorCode.TENANT_PROTOCOL_UNSUPPORTED,
                    "未找到匹配的租户申请协议流水线");
        }
        if (matchedPipelines.size() > 1) {
            throw new ApplicationIntakeException(ApplicationIntakeErrorCode.TENANT_PROTOCOL_AMBIGUOUS,
                    "租户申请协议流水线配置冲突");
        }
        ApplicationIntakeCommand command = matchedPipelines.getFirst().execute(rawPayload);
        if (command == null || !tenantInfo.tenantCode().equals(command.tenantCode())) {
            throw new ApplicationIntakeException(ApplicationIntakeErrorCode.APPLICATION_INTAKE_INVALID,
                    "租户申请协议转换结果无效");
        }
        return command;
    }
}
