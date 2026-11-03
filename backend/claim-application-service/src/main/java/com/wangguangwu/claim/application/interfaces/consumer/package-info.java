/**
 * MQ 入站适配。
 *
 * <p>消费材料处理、审核交付与审核签收消息；消费者位于本包，消息解析与命令装配统一放在
 * {@code message} 子包，不承载业务状态规则。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.interfaces.consumer;
