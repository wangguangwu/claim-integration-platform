package com.wangguangwu.claim.application.infrastructure.underwriting.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 承保系统 Feign 接口，本地指向本服务的模拟实现，生产替换地址即可。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@FeignClient(name = "claim-underwriting", url = "${claim.integration.underwriting.url}")
public interface UnderwritingFeignClient {

    /**
     * 查询被保人是否已有有效保单。
     *
     * @param request 保单查询请求
     * @return 保单查询结果
     */
    @PostMapping("/mock/underwriting/v1/policy-queries")
    PolicyQueryResponse queryPolicy(@RequestBody PolicyQueryRequest request);

    /**
     * 发起自动承保，按平台申请编号幂等。
     *
     * @param request 自动承保请求
     * @return 自动承保受理结果
     */
    @PostMapping("/mock/underwriting/v1/auto-underwritings")
    AutoUnderwritingResponse requestAutoUnderwriting(@RequestBody AutoUnderwritingRequest request);

    /**
     * 查询自动承保结果。
     *
     * @param applicationNo 平台申请编号
     * @return 承保结果
     */
    @GetMapping("/mock/underwriting/v1/auto-underwritings/{applicationNo}")
    AutoUnderwritingResponse queryAutoUnderwriting(@PathVariable("applicationNo") String applicationNo);

    /**
     * 保单查询请求。
     *
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     */
    record PolicyQueryRequest(String tenantCode, String insuredCertificateNo) {
    }

    /**
     * 保单查询结果。
     *
     * @param hasValidPolicy 是否存在有效保单
     * @param policyNo       保单号
     */
    record PolicyQueryResponse(boolean hasValidPolicy, String policyNo) {
    }

    /**
     * 自动承保请求。
     *
     * @param applicationNo        平台申请编号
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     */
    record AutoUnderwritingRequest(String applicationNo, String tenantCode, String insuredCertificateNo) {
    }

    /**
     * 自动承保受理与查询结果。
     *
     * @param applicationNo 平台申请编号
     * @param status        承保状态：PROCESSING、COMPLETED 或 NOT_FOUND
     */
    record AutoUnderwritingResponse(String applicationNo, String status) {
    }
}
