package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core.pipeline;

import com.wangguangwu.claim.application.usecase.intake.command.ClaimApplicationIntakeCommand;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;

/**
 * 租户私有协议接入流水线端口，每种租户协议对应一条流水线。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface TenantProtocolPipeline {

    /**
     * 判断当前流水线是否支持指定租户和协议版本。
     *
     * @param tenantInfo 当前租户信息
     * @return 支持时为 true
     */
    boolean supports(TenantInfo tenantInfo);

    /**
     * 依次执行验签、解密、解析、校验与转换，输出平台统一的受理命令。
     *
     * @param rawPayload 原始请求正文
     * @return 受理命令，租户编码必须与上下文一致
     */
    ClaimApplicationIntakeCommand execute(String rawPayload);
}
