package com.wangguangwu.claim.application.usecase.query;

import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationQueryErrorCode;
import com.wangguangwu.claim.application.usecase.exception.ApplicationException;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithCases;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithIssues;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithMaterials;
import com.wangguangwu.claim.application.usecase.query.port.ClaimApplicationDetailQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 理赔申请详情查询用例，按租户与申请编号返回读模型。
 *
 * <p>查询不加载领域聚合、不写交互轨迹；申请不存在与租户不匹配使用同一错误码，避免暴露跨租户数据存在性。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationDetailQueryUseCase {

    private final ClaimApplicationDetailQuery claimApplicationDetailQuery;

    /**
     * 查询申请摘要与未删除材料。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 申请摘要与材料
     */
    public ClaimApplicationWithMaterials findWithMaterials(String tenantCode, String claimApplicationNo) {
        return claimApplicationDetailQuery.findWithMaterials(tenantCode, claimApplicationNo)
                .orElseThrow(() -> notFound(claimApplicationNo));
    }

    /**
     * 查询申请摘要与全部问题件。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 申请摘要与问题件
     */
    public ClaimApplicationWithIssues findWithIssues(String tenantCode, String claimApplicationNo) {
        return claimApplicationDetailQuery.findWithIssues(tenantCode, claimApplicationNo)
                .orElseThrow(() -> notFound(claimApplicationNo));
    }

    /**
     * 查询申请摘要与案件关联。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 申请摘要与案件关联
     */
    public ClaimApplicationWithCases findWithCases(String tenantCode, String claimApplicationNo) {
        return claimApplicationDetailQuery.findWithCases(tenantCode, claimApplicationNo)
                .orElseThrow(() -> notFound(claimApplicationNo));
    }

    private ApplicationException notFound(String claimApplicationNo) {
        return new ApplicationException(ClaimApplicationQueryErrorCode.CLAIM_APPLICATION_QUERY_NOT_FOUND,
                "理赔申请不存在，claimApplicationNo=" + claimApplicationNo);
    }
}
