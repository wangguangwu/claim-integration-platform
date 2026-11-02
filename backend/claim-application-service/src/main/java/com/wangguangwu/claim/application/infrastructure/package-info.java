/**
 * 理赔申请服务的基础设施适配层。
 *
 * <p>实现领域端口并承载 MyBatis-Plus 持久化、Redis、Redisson、消息存储和第三方系统本地适配；
 * 仅在此层引入具体技术框架，向上通过领域端口提供能力。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.infrastructure;
