package com.wangguangwu.claim.application.infrastructure.underwriting.external;

import com.wangguangwu.claim.application.domain.underwriting.port.AutoUnderwritingRequester;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 自动承保发起的 Feign 适配器，发起失败时异常向上抛出。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Component
public class FeignAutoUnderwritingRequester implements AutoUnderwritingRequester {

    private final UnderwritingFeignClient underwritingFeignClient;

    /**
     * 按平台申请编号发起自动承保，外部系统保证重复发起幂等。
     *
     * @param claimApplicationNo        平台申请编号
     * @param tenantCode           租户编码
     * @param insuredCertificateNo 被保人证件号
     */
    @Override
    public void request(String claimApplicationNo, String tenantCode, String insuredCertificateNo) {
        underwritingFeignClient.requestAutoUnderwriting(
                new UnderwritingFeignClient.AutoUnderwritingRequest(claimApplicationNo, tenantCode,
                        insuredCertificateNo));
    }
}
