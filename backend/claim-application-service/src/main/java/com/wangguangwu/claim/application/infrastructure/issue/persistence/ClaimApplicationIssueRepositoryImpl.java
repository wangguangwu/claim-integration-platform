package com.wangguangwu.claim.application.infrastructure.issue.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssueRepository;
import com.wangguangwu.claim.application.domain.issue.ClaimApplicationIssueStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的问题件聚合仓储实现。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@RequiredArgsConstructor
@Repository
public class ClaimApplicationIssueRepositoryImpl implements ClaimApplicationIssueRepository {

    private final ClaimApplicationIssueMapper issueMapper;
    private final ClaimApplicationIssueConverter issueConverter;

    /**
     * 按问题件编号查询，编号全局唯一。
     *
     * @param issueNo 问题件编号
     * @return 问题件聚合
     */
    @Override
    public Optional<ClaimApplicationIssue> findByIssueNo(String issueNo) {
        return Optional.ofNullable(issueMapper.selectOne(Wrappers.lambdaQuery(ClaimApplicationIssueDO.class)
                        .eq(ClaimApplicationIssueDO::getIssueNo, issueNo)))
                .map(issueConverter::toDomain);
    }

    /**
     * 判断申请是否有未关闭问题件。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 存在 OPEN 状态问题件时为 true
     */
    @Override
    public boolean hasOpenIssue(String claimApplicationNo) {
        return issueMapper.selectCount(Wrappers.lambdaQuery(ClaimApplicationIssueDO.class)
                        .eq(ClaimApplicationIssueDO::getClaimApplicationNo, claimApplicationNo)
                        .eq(ClaimApplicationIssueDO::getIsOpen, 1))
                > 0;
    }

    /**
     * 插入新问题件，并回填数据库生成主键。
     *
     * @param issue 待插入问题件
     */
    @Override
    public void insert(ClaimApplicationIssue issue) {
        ClaimApplicationIssueDO issueDO = issueConverter.toDataObject(issue);
        issueMapper.insert(issueDO);
        issue = ClaimApplicationIssue.restore(
                issueDO.getId(),
                issue.getClaimApplicationNo(),
                issue.getIssueNo(),
                issue.getReason(),
                issue.getAuditor(),
                issue.getIssueContent(),
                issue.getStatus(),
                issue.getClosedAt(),
                issue.getCreatedAt(),
                issue.getUpdatedAt()
        );
    }

    /**
     * 更新问题件状态与关闭时间。
     *
     * @param issue 已变更的问题件聚合
     */
    @Override
    public void update(ClaimApplicationIssue issue) {
        ClaimApplicationIssueDO issueDO = issueConverter.toDataObject(issue);
        issueMapper.update(null, Wrappers.lambdaUpdate(ClaimApplicationIssueDO.class)
                .eq(ClaimApplicationIssueDO::getId, issueDO.getId())
                .set(ClaimApplicationIssueDO::getStatus, issueDO.getStatus())
                .set(ClaimApplicationIssueDO::getIsOpen, issueDO.getIsOpen())
                .set(ClaimApplicationIssueDO::getAuditor, issueDO.getAuditor())
                .set(ClaimApplicationIssueDO::getClosedAt, issueDO.getClosedAt()));
    }
}
