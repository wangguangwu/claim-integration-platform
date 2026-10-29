package com.wangguangwu.claim.application.domain.underwriting.port;

import com.wangguangwu.claim.application.domain.underwriting.UnderwritingResult;

/**
 * 自动承保结果查询端口，供补偿任务判断是否可以把申请推进到等待审核签收。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface UnderwritingResultQuery {

    /**
     * 按平台申请编号查询自动承保结果。
     *
     * @param applicationNo 平台申请编号
     * @return 承保结果；查不到记录时返回 {@link UnderwritingResult#NOT_FOUND}
     */
    UnderwritingResult query(String applicationNo);
}
