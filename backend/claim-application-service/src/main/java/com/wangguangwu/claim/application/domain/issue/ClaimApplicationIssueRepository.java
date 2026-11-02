package com.wangguangwu.claim.application.domain.issue;

import java.util.Optional;

/**
 * 问题件聚合的持久化端口。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
public interface ClaimApplicationIssueRepository {

    /**
     * 按问题件编号查询。
     *
     * @param issueNo 问题件编号
     * @return 问题件聚合
     */
    Optional<ClaimApplicationIssue> findByIssueNo(String issueNo);

    /**
     * 判断申请是否有未关闭问题件。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 存在 OPEN 状态问题件时为 true
     */
    boolean hasOpenIssue(String claimApplicationNo);

    /**
     * 查询申请当前未关闭的问题件。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 未关闭问题件；无未关闭问题件时为空
     */
    Optional<ClaimApplicationIssue> findOpenByClaimApplicationNo(String claimApplicationNo);

    /**
     * 插入新问题件。
     *
     * @param issue 待插入问题件
     */
    void insert(ClaimApplicationIssue issue);

    /**
     * 更新问题件状态。
     *
     * @param issue 已变更的问题件聚合
     */
    void update(ClaimApplicationIssue issue);
}
