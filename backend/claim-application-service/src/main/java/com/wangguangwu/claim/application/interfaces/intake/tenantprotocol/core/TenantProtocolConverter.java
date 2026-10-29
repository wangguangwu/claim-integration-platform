package com.wangguangwu.claim.application.interfaces.intake.tenantprotocol.core;

import com.wangguangwu.claim.application.usecase.intake.command.ApplicationIntakeCommand;

/**
 * 租户 request 到平台统一受理命令的转换器，负责字段归一化。
 *
 * <p>转换器只处理已通过 {@link TenantProtocolValidator} 校验的 request；
 * 无法归一化的枚举编码在此处按字段拒绝。</p>
 *
 * @param <R> 租户 request 类型
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface TenantProtocolConverter<R> {

    /**
     * 将租户 request 转换为签收用例的标准命令。
     *
     * @param tenantCode 当前租户编码
     * @param request    已校验的租户 request
     * @return 尚未分配平台申请编号的受理命令
     */
    ApplicationIntakeCommand convert(String tenantCode, R request);
}
