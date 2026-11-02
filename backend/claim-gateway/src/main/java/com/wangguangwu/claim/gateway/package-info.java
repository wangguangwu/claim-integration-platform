/**
 * 网关服务模块。
 *
 * <p>负责请求进入业务服务前的链路标识透传、租户上下文校验、Sentinel 限流和运行时监控配置；
 * 不承载理赔申请领域规则或持久化逻辑。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.gateway;
