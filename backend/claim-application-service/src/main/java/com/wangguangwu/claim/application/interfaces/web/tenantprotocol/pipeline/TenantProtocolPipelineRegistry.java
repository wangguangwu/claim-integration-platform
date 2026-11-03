package com.wangguangwu.claim.application.interfaces.web.tenantprotocol.pipeline;

import com.wangguangwu.claim.application.shared.exception.BusinessException;
import com.wangguangwu.claim.application.application.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 租户协议流水线注册中心，按租户编码和协议版本定位唯一流水线。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Component
public class TenantProtocolPipelineRegistry {

    private final List<TenantProtocolPipeline> pipelines;

    /**
     * 获取与租户上下文唯一匹配的协议流水线。
     *
     * @param tenantInfo 当前租户信息
     * @return 租户协议流水线
     */
    public TenantProtocolPipeline getPipeline(TenantInfo tenantInfo) {
        if (tenantInfo == null || !StringUtils.hasText(tenantInfo.tenantCode())
                || !StringUtils.hasText(tenantInfo.protocolVersion())) {
            throw new BusinessException("缺少租户编码或协议版本");
        }
        List<TenantProtocolPipeline> matchedPipelines = pipelines.stream()
                .filter(pipeline -> pipeline.supports(tenantInfo))
                .toList();
        if (matchedPipelines.isEmpty()) {
            throw new BusinessException("未找到匹配的租户申请协议流水线");
        }
        if (matchedPipelines.size() > 1) {
            throw new BusinessException("租户申请协议流水线配置冲突");
        }
        return matchedPipelines.get(0);
    }

    /**
     * 执行租户协议适配并校验转换结果未跨租户。
     *
     * @param tenantInfo 当前租户信息
     * @param rawPayload 租户原始请求正文
     * @return 签收用例的标准命令
     */
    public ClaimApplicationIntakeCommand execute(TenantInfo tenantInfo, String rawPayload) {
        ClaimApplicationIntakeCommand command = getPipeline(tenantInfo).execute(rawPayload);
        if (command == null || !tenantInfo.tenantCode().equals(command.tenantCode())) {
            throw new BusinessException("租户申请协议转换结果无效");
        }
        return command;
    }
}
