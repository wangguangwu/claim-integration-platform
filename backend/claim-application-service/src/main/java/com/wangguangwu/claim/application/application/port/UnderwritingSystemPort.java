package com.wangguangwu.claim.application.application.port;

import com.wangguangwu.claim.application.application.port.model.UnderwritingResult;

/**
 * 承保系统端口：有效保单查询、自动承保发起与承保结果查询。
 *
 * <p>三项能力属于同一外部系统，合并为一个端口；接入真实承保系统时由基础设施层统一替换。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public interface UnderwritingSystemPort {

    /**
     * 判断租户下被保人是否存在有效保单；分流只依赖存在性，不消费保单细节。
     *
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     * @return 存在有效保单时为 true
     */
    boolean hasValidPolicy(String tenantCode, String insuredCertificateNo);

    /**
     * 发起自动承保；按平台申请编号幂等，重复发起不得产生多份承保记录。
     *
     * @param claimApplicationNo        平台申请编号
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     */
    void requestAutoUnderwriting(String claimApplicationNo, String tenantCode, String insuredCertificateNo);

    /**
     * 查询自动承保结果，供承保中滞留补偿判断下一步动作。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 承保结果；查不到任务时返回 {@link UnderwritingResult#NOT_FOUND}
     */
    UnderwritingResult queryUnderwritingResult(String claimApplicationNo);
}
