package com.wangguangwu.claim.application.interfaces.adapter;

import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;
import com.wangguangwu.claim.foundation.tenant.context.TenantInfo;

/**
 * 租户私有协议接入流水线端口，每种租户协议对应一条流水线。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface TenantApplicationPipeline {

    /**
     * 判断当前流水线是否支持指定租户和协议版本。
     *
     * @param tenantInfo 当前租户信息
     * @return 支持时为 true
     */
    boolean supports(TenantInfo tenantInfo);

    /**
     * 依次执行解析、校验与转换，输出标准签收命令。
     *
     * @param rawPayload 原始请求正文
     * @return 标准化签收命令，命令中的租户编码必须与上下文一致
     */
    ApplicationIntakeCommand execute(String rawPayload);
}
