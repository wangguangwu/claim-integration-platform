package com.wangguangwu.claim.application.shared.message;

/**
 * 出站消息契约，只有本服务经 Outbox 投递的消息可以实现。
 *
 * <p>入站专属的审核签收消息不能传入投递端口，避免运行时再做事件类型兜底判断。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public sealed interface OutboundClaimApplicationEventMessage extends ClaimApplicationEventMessage
        permits MaterialProcessMessage, AuditDeliveryRequestedMessage {
}
