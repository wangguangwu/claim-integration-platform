package com.wangguangwu.claim.application.interfaces.adapter;

import com.wangguangwu.claim.application.usecase.command.ApplicationIntakeCommand;

/**
 * 租户 request 到标准签收命令的转换器，负责字段归一化。
 *
 * <p>转换器只处理已通过 {@link TenantApplicationValidator} 校验的 request；
 * 无法归一化的枚举编码在此处按字段拒绝。</p>
 *
 * @param <R> 租户 request 类型
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface TenantApplicationConverter<R> {

    /**
     * 将租户 request 转换为标准签收命令。
     *
     * @param tenantCode 当前租户编码
     * @param request    已校验的租户 request
     * @return 标准化签收命令
     */
    ApplicationIntakeCommand convert(String tenantCode, R request);
}
