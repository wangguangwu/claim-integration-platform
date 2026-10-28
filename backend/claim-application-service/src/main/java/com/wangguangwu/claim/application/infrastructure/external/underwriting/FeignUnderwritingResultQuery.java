package com.wangguangwu.claim.application.infrastructure.external.underwriting;

import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResult;
import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResultQuery;
import org.springframework.stereotype.Component;

/**
 * 自动承保结果查询的 Feign 适配器，供补偿任务判断是否推进申请状态。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Component
public class FeignUnderwritingResultQuery implements UnderwritingResultQuery {

    private final UnderwritingFeignClient underwritingFeignClient;

    public FeignUnderwritingResultQuery(UnderwritingFeignClient underwritingFeignClient) {
        this.underwritingFeignClient = underwritingFeignClient;
    }

    /**
     * 按平台申请编号查询承保结果。
     *
     * @param applicationNo 平台申请编号
     * @return 承保结果
     */
    @Override
    public UnderwritingResult query(String applicationNo) {
        UnderwritingFeignClient.AutoUnderwritingResponse response =
                underwritingFeignClient.queryAutoUnderwriting(applicationNo);
        return UnderwritingResult.valueOf(response.status());
    }
}
