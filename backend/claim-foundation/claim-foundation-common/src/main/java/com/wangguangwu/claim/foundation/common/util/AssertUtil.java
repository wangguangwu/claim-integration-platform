package com.wangguangwu.claim.foundation.common.util;

import java.util.Collection;

/**
 * 参数前置条件断言工具，统一校验非法入参并保留调用方定义的错误信息。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public final class AssertUtil {

    private AssertUtil() {
    }

    /**
     * 断言字符串非空白。
     *
     * @param value   待校验字符串
     * @param message 断言失败时的异常消息
     */
    public static void notBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言对象非空。
     *
     * @param value   待校验对象
     * @param message 断言失败时的异常消息
     */
    public static void notNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言集合非空。
     *
     * @param value   待校验集合
     * @param message 断言失败时的异常消息
     */
    public static void notEmpty(Collection<?> value, String message) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言条件成立。
     *
     * @param expression 待校验条件
     * @param message    断言失败时的异常消息
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }
}
