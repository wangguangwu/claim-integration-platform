package com.wangguangwu.claim.foundation.observability.flow;

import com.wangguangwu.claim.foundation.common.logging.MdcContext;
import com.wangguangwu.claim.foundation.common.logging.MdcKeys;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;

/**
 * 流程日志切面：记录被 {@link FlowLog} 标注方法的开始、完成与失败。
 *
 * <p>日志只包含流程名、耗时与异常摘要，不输出方法参数、返回值或请求报文。
 * 切面不吞异常；嵌套流程结束后恢复外层流程名。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@Aspect
public class FlowLogAspect {

    /**
     * 环绕流程入口，记录流程边界并在结束后恢复 MDC。
     *
     * @param joinPoint 流程入口连接点
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("@annotation(com.wangguangwu.claim.foundation.observability.flow.FlowLog)")
    public Object logFlow(ProceedingJoinPoint joinPoint) throws Throwable {
        String flow = resolveFlowName(joinPoint);
        String previousFlow = MdcContext.get(MdcKeys.FLOW);
        MdcContext.put(MdcKeys.FLOW, flow);
        long startedAt = System.currentTimeMillis();
        log.info("流程开始执行，flow={}", flow);
        try {
            Object result = joinPoint.proceed();
            log.info("流程执行完成，flow={}，durationMs={}", flow, elapsedMillis(startedAt));
            return result;
        } catch (Throwable throwable) {
            log.warn("流程执行失败，flow={}，durationMs={}，reason={}", flow, elapsedMillis(startedAt),
                    throwable.toString());
            throw throwable;
        } finally {
            restoreFlow(previousFlow);
        }
    }

    private String resolveFlowName(ProceedingJoinPoint joinPoint) {
        FlowLog flowLog = resolveFlowLog(joinPoint);
        return flowLog == null ? joinPoint.getSignature().toShortString() : flowLog.value();
    }

    private FlowLog resolveFlowLog(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature) || joinPoint.getTarget() == null) {
            return null;
        }
        Method method = signature.getMethod();
        FlowLog flowLog = method.getAnnotation(FlowLog.class);
        if (flowLog != null) {
            return flowLog;
        }
        Method targetMethod = AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
        return targetMethod.getAnnotation(FlowLog.class);
    }

    private long elapsedMillis(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private void restoreFlow(String previousFlow) {
        if (previousFlow == null) {
            MdcContext.remove(MdcKeys.FLOW);
            return;
        }
        MdcContext.put(MdcKeys.FLOW, previousFlow);
    }
}
