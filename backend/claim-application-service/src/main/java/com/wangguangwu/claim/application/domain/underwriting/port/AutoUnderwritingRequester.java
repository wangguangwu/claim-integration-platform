package com.wangguangwu.claim.application.domain.underwriting.port;

/**
 * 自动承保发起端口。
 *
 * <p>发起按平台申请编号幂等，重复发起不得产生多份承保记录；承保结果通过完成通知或结果查询获得。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface AutoUnderwritingRequester {

    /**
     * 发起自动承保。
     *
     * @param claimApplicationNo        平台申请编号
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     */
    void request(String claimApplicationNo, String tenantCode, String insuredCertificateNo);
}
