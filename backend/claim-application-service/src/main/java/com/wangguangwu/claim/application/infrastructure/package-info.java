/**
 * 理赔申请服务的基础设施适配层。
 *
 * <p>按技术角色拆分：{@code persistence} 实现聚合仓储与读模型，{@code adapter} 实现应用端口，
 * {@code mq} 负责消息发布，{@code config} 装配框架与业务配置；仅在此层引入具体技术框架。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.infrastructure;
