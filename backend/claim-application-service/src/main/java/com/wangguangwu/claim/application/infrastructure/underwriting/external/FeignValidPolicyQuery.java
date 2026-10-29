package com.wangguangwu.claim.application.infrastructure.underwriting.external;

import com.wangguangwu.claim.application.domain.underwriting.PolicySnapshot;
import com.wangguangwu.claim.application.domain.underwriting.ValidPolicyQuery;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 有效保单查询的 Feign 适配器，承保系统不可用时异常向上抛出。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Component
public class FeignValidPolicyQuery implements ValidPolicyQuery {

    private final UnderwritingFeignClient underwritingFeignClient;

    /**
     * 按租户与被保人证件号查询有效保单。
     *
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     * @return 有效保单；不存在时为空
     */
    @Override
    public Optional<PolicySnapshot> findValidPolicy(String tenantCode, String insuredCertificateNo) {
        UnderwritingFeignClient.PolicyQueryResponse response = underwritingFeignClient.queryPolicy(
                new UnderwritingFeignClient.PolicyQueryRequest(tenantCode, insuredCertificateNo));
        if (!response.hasValidPolicy()) {
            return Optional.empty();
        }
        return Optional.of(new PolicySnapshot(response.policyNo()));
    }
}
