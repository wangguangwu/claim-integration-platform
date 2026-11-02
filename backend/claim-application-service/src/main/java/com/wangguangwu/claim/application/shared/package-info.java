/**
 * 理赔申请服务的受控共享能力。
 *
 * <p>仅放置跨多个业务能力复用的事件契约和无业务归属的基础设施小组件；HTTP 响应模型归属入站适配层。
 * 新增代码应先判断是否属于某个明确业务域，避免把 {@code shared} 演变为无边界的工具包。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.shared;
