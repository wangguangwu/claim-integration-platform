package com.wangguangwu.claim.application.domain.underwriting;

import java.util.Optional;

/**
 * 有效保单查询端口。
 *
 * <p>承保系统不属于本项目实现范围，基础设施层以外部接口适配，本地以模拟实现返回内存数据。
 * 承保系统不可用或超时时抛出异常，由调用方保持原状态等待重试。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface ValidPolicyQuery {

    /**
     * 按租户与被保人证件号查询有效保单。
     *
     * @param tenantCode             租户编码
     * @param insuredCertificateNo   被保人证件号
     * @return 有效保单；不存在时为空
     */
    Optional<PolicySnapshot> findValidPolicy(String tenantCode, String insuredCertificateNo);
}
