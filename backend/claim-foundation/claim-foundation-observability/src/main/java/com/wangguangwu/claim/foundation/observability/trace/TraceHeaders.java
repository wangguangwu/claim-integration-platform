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

    private TraceHeaders() {
    }
}
