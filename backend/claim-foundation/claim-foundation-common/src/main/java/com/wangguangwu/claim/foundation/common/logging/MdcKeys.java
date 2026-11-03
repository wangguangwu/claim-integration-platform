package com.wangguangwu.claim.foundation.common.logging;

/**
 * 日志 MDC 键常量。
 *
 * <p>链路、租户与流程标识统一使用这里的键，避免各模块自行定义字符串导致日志字段不一致。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public final class MdcKeys {

    /**
     * 链路标识。
     */
    public static final String TRACE_ID = "traceId";

    /**
     * 租户编码。
     */
    public static final String TENANT_CODE = "tenantCode";

    /**
     * 协议版本。
     */
    public static final String PROTOCOL_VERSION = "protocolVersion";

    /**
     * 当前流程名称。
     */
    public static final String FLOW = "flow";

    /**
     * 消息事件标识。
     */
    public static final String EVENT_ID = "eventId";

    private MdcKeys() {
    }
}
