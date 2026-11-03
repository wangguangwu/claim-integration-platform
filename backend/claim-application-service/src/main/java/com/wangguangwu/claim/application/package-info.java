/**
 * 理赔申请服务的业务实现模块。
 *
 * <p>本包按 DDD 分层组织：{@code domain} 定义业务规则与聚合仓储，{@code application} 编排用例并声明出站端口，
 * {@code infrastructure} 实现持久化、缓存、消息和外部系统适配，{@code interfaces} 承接 HTTP、消息和调度入口，
 * {@code shared} 只放跨层复用的底层契约。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application;
