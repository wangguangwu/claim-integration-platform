package com.wangguangwu.claim.application.application.port;

import com.wangguangwu.claim.application.application.port.model.AuditDeliveryRequest;
import com.wangguangwu.claim.application.application.port.model.AuditSignedStatus;

/**
 * 审核系统端口：交付等待审核的申请并查询签收状态。
 *
 * <p>交付与签收查询属于同一外部系统，合并为一个端口；接入真实审核系统时由基础设施层统一替换。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public interface AuditSystemPort {

    /**
     * 交付申请；按平台申请编号幂等，重复交付不得在审核系统产生多份待办。
     *
     * @param request 交付入参
     */
    void deliver(AuditDeliveryRequest request);

    /**
     * 查询指定申请的审核签收状态。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 审核签收状态
     */
    AuditSignedStatus querySignedStatus(String claimApplicationNo);
}
