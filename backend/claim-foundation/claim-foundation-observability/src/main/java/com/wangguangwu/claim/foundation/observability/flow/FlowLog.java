package com.wangguangwu.claim.foundation.observability.flow;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记流程入口方法，由切面统一记录流程开始、完成与失败日志。
 *
 * <p>只允许标注在公开入口方法上；类内自调用与私有方法不会被代理拦截。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlowLog {

    /**
     * 流程名称，写入日志与 MDC。
     *
     * @return 流程名称
     */
    String value();
}
