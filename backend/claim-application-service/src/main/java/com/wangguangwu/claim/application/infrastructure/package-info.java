/**
 * 理赔申请服务的基础设施适配层。
 *
 * <p>实现 usecase 出站端口与 domain 聚合 Repository，并承载 MyBatis-Plus 持久化、Redis、Redisson、消息存储
 * 和第三方系统本地适配；仅在此层引入具体技术框架，向上通过端口与仓储提供能力。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.infrastructure;
