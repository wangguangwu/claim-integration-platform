package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core;

import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeErrorCode;
import com.wangguangwu.claim.application.domain.claim.exception.ApplicationIntakeException;
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
            throw ApplicationIntakeException.invalid("缺少租户编码或协议版本");
        }
        List<TenantProtocolPipeline> matchedPipelines = pipelines.stream()
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
        return matchedPipelines.getFirst();
    }
}
