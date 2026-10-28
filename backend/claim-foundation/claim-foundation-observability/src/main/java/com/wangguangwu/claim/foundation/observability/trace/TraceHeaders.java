package com.wangguangwu.claim.foundation.observability.trace;

/**
 * 链路相关请求头。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public final class TraceHeaders {

    /**
     * 链路标识。
     */
    public static final String TRACE_ID = "X-Trace-Id";

    /**
     * 请求标识，用于关联同一租户请求的交互事件。
     */
    public static final String REQUEST_ID = "X-Request-Id";

    private TraceHeaders() {
    }
}
