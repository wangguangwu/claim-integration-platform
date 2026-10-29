package com.wangguangwu.claim.application.infrastructure.underwriting.external;

import com.wangguangwu.claim.application.domain.underwriting.port.ValidPolicyQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 有效保单存在性查询的 Feign 适配器，承保系统不可用时异常向上抛出。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Component
public class FeignValidPolicyQuery implements ValidPolicyQuery {

    private final UnderwritingFeignClient underwritingFeignClient;

    /**
     * 查询被保人是否存在有效保单。
     *
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     * @return 存在有效保单时为 true
     */
    @Override
    public boolean hasValidPolicy(String tenantCode, String insuredCertificateNo) {
        UnderwritingFeignClient.PolicyQueryResponse response = underwritingFeignClient.queryPolicy(
                new UnderwritingFeignClient.PolicyQueryRequest(tenantCode, insuredCertificateNo));
        return response.hasValidPolicy();
    }
}
