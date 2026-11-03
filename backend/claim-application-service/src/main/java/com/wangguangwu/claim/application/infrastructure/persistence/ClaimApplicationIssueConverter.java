package com.wangguangwu.claim.application.infrastructure.persistence;

import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssue;
import com.wangguangwu.claim.application.domain.claim.issue.ClaimApplicationIssueStatus;
import org.springframework.stereotype.Component;

/**
 * 问题件数据对象与领域聚合之间的转换器。
 *
 * <p>写方向逐字段映射，读方向显式调用聚合自己的反水化工厂，不用 setter 绕过状态机约束。
 * 这里不使用 MapStruct 生成：聚合根不允许暴露 setter，而 MapStruct 无法向抽象 mapper
 * 基类做构造器注入。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Component
public class ClaimApplicationIssueConverter {

    /**
     * 转换为持久化数据对象，不携带归属申请编号，供按主键更新使用。
     *
     * @param issue 问题件实体
     * @return 数据对象
     */
    public ClaimApplicationIssueDO toDataObject(ClaimApplicationIssue issue) {
        return toDataObject(null, issue);
    }

    /**
     * 转换为持久化数据对象并写入问题件所属申请编号；创建与更新时间由数据库维护，不随领域对象写入。
     *
     * @param claimApplicationNo 问题件所属平台申请编号
     * @param issue              问题件实体
     * @return 数据对象
     */
    public ClaimApplicationIssueDO toDataObject(String claimApplicationNo, ClaimApplicationIssue issue) {
        if (issue == null) {
            return null;
        }
        ClaimApplicationIssueStatus status = issue.getStatus();
        return ClaimApplicationIssueDO.builder()
                .id(issue.getId())
                .claimApplicationNo(claimApplicationNo)
                .issueNo(issue.getIssueNo())
                .reason(issue.getReason())
                .raisedBy(issue.getRaisedBy())
                .closedBy(issue.getClosedBy())
                .issueContentJson(issue.getIssueContent())
                .status(status == null ? null : status.name())
                .isOpen(issue.isOpen() ? 1 : 0)
                .closedAt(issue.getClosedAt())
                .build();
    }

    /**
     * 从数据对象重建问题件实体。
     *
     * @param issueDO 数据对象
     * @return 问题件实体
     */
    public ClaimApplicationIssue toDomain(ClaimApplicationIssueDO issueDO) {
        if (issueDO == null) {
            return null;
        }
        return ClaimApplicationIssue.restore(
                issueDO.getId(),
                issueDO.getIssueNo(),
                issueDO.getReason(),
                issueDO.getRaisedBy(),
                issueDO.getClosedBy(),
                issueDO.getIssueContentJson(),
                ClaimApplicationIssueStatus.valueOf(issueDO.getStatus()),
                issueDO.getClosedAt(),
                issueDO.getCreatedAt(),
                issueDO.getUpdatedAt()
        );
    }
}
