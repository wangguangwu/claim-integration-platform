/**
 * MQ 消息解析与命令装配。
 *
 * <p>只做报文严格反序列化与命令字段映射，不校验业务规则、不访问仓储、不发起事务；
 * 由本包上游的消费者调用。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
package com.wangguangwu.claim.application.interfaces.consumer.message;
