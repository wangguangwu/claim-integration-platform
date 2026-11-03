package com.wangguangwu.claim.application.usecase.query.port;

import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithCases;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithIssues;
import com.wangguangwu.claim.application.usecase.query.model.ClaimApplicationWithMaterials;
import com.wangguangwu.claim.application.usecase.query.model.IssueView;
import java.util.Optional;

/**
 * 理赔申请详情读模型端口。
 *
 * <p>所有方法同时按租户与申请编号定位，租户不匹配按不存在返回空；实现直接读取数据表，
 * 不加载领域聚合、不写任何数据。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public interface ClaimApplicationDetailQuery {

    /**
     * 查询申请摘要与未删除材料。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 申请摘要与材料；申请不存在或租户不匹配时为空
     */
    Optional<ClaimApplicationWithMaterials> findWithMaterials(String tenantCode, String claimApplicationNo);

    /**
     * 查询申请摘要与全部问题件。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 申请摘要与问题件；申请不存在或租户不匹配时为空
     */
    Optional<ClaimApplicationWithIssues> findWithIssues(String tenantCode, String claimApplicationNo);

    /**
     * 查询申请摘要与案件关联。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 申请摘要与案件关联；申请不存在或租户不匹配时为空
     */
    Optional<ClaimApplicationWithCases> findWithCases(String tenantCode, String claimApplicationNo);

    /**
     * 查询申请当前未关闭的问题件。
     *
     * @param tenantCode         租户编码
     * @param claimApplicationNo 平台申请编号
     * @return 未关闭问题件；不存在或租户不匹配时为空
     */
    Optional<IssueView> findOpenIssue(String tenantCode, String claimApplicationNo);
}
