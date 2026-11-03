/**
 * 租户协议接入流水线。
 *
 * <p>所有租户共用签收入口，按租户编码与协议版本路由到各自流水线；每个租户注册一个流水线实例，
 * 当前统一使用默认实现，阶段实现与租户流水线可独立扩展。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.interfaces.web.tenantprotocol;
