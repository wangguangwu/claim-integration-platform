/**
 * 理赔申请服务的应用用例层。
 *
 * <p>按签收、材料、承保、审核、问题件和异步集成组织业务编排；负责事务边界、端口调用顺序和幂等结果归类，
 * 并声明出站能力端口、配置策略端口与端口模型。不直接依赖 MyBatis-Plus Mapper、Redis 客户端或 HTTP/MQ 框架入口。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.usecase;
