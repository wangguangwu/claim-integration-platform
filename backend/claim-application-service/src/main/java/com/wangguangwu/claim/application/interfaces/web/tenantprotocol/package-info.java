/**
 * 租户协议接入流水线。
 *
 * <p>所有租户共用签收入口，按租户编码与协议版本路由到各自流水线；每个租户注册一条独立流水线，
 * 接入真实协议前使用平台标准报文转换为标准受理命令。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.interfaces.web.tenantprotocol;
