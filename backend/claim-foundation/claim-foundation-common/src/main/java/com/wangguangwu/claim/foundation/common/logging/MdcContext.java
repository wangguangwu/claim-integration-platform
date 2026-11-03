package com.wangguangwu.claim.foundation.common.logging;

import org.slf4j.MDC;

/**
 * 日志 MDC 读写工具。
 *
 * <p>空值不写入并移除同名键，避免线程复用后残留上一次请求的租户或链路标识。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public final class MdcContext {

    private MdcContext() {
    }

    /**
     * 写入 MDC，空值视为移除该键。
     *
     * @param key   键
     * @param value 值
     */
    public static void put(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }

    /**
     * 读取 MDC 值。
     *
     * @param key 键
     * @return 值；不存在时为空
     */
    public static String get(String key) {
        return MDC.get(key);
    }

    /**
     * 移除单个 MDC 键。
     *
     * @param key 键
     */
    public static void remove(String key) {
        MDC.remove(key);
    }

    /**
     * 清理当前线程的全部 MDC。
     */
    public static void clear() {
        MDC.clear();
    }
}
