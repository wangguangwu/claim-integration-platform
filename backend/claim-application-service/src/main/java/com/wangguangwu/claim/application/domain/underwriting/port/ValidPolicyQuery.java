package com.wangguangwu.claim.application.domain.underwriting.port;

/**
 * 有效保单存在性查询端口。
 *
 * <p>承保系统不属于本项目实现范围，基础设施层以外部接口适配，本地以模拟实现返回内存数据。
 * 承保系统不可用或超时时抛出异常，由调用方保持原状态等待重试。</p>
 *
 * @author wangguangwu
 * @since 2026-10-29
 */
public interface ValidPolicyQuery {

    /**
     * 判断租户下被保人是否存在有效保单；分流只依赖存在性，不消费保单细节。
     *
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     * @return 存在有效保单时为 true
     */
    boolean hasValidPolicy(String tenantCode, String insuredCertificateNo);
}
