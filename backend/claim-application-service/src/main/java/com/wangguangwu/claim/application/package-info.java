/**
 * 理赔申请服务的业务实现模块。
 *
 * <p>本包按 DDD 分层组织：{@code domain} 定义业务规则与端口，{@code usecase} 编排业务用例，
 * {@code infrastructure} 实现数据库、缓存、消息和第三方适配，{@code interfaces} 承接 HTTP、消息和调度入口。
 * 从业务能力定位代码时，优先查看代码导航文档中的签收、材料、承保、审核、问题件和异步集成映射。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application;
