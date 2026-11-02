/**
 * 网关侧租户上下文校验。
 *
 * <p>在路由前检查租户编码和协议版本，缺失时直接拒绝请求，避免无租户上下文进入业务服务。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.gateway.tenant;
