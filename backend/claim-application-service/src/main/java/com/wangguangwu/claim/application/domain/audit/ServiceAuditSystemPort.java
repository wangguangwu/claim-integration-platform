package com.wangguangwu.claim.application.domain.audit;

/**
 * 审核系统交付端口，把进入等待审核签收的申请交付给审核系统。
 *
 * <p>审核系统不属于本项目实现范围，基础设施层以进程内实现按配置返回；
 * 交付不可用或失败时抛出异常，由调用方保留原状态等待消息重投。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
public interface ServiceAuditSystemPort {

    /**
     * 交付申请；按平台申请编号幂等，重复交付不得在审核系统产生多份待办。
     *
     * @param request 交付入参
     */
    void deliver(AuditDeliveryRequest request);
}
