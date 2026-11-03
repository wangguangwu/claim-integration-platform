package com.wangguangwu.claim.application.domain.claim.issue;

import com.wangguangwu.claim.application.domain.claim.issue.exception.ClaimApplicationIssueErrorCode;
import com.wangguangwu.claim.application.domain.exception.DomainException;
import com.wangguangwu.claim.foundation.common.util.AssertUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 理赔申请内的问题件实体，封装审核系统下发的问题件生命周期。
 *
 * <p>问题件不属于独立聚合，不持有平台申请编号；归属由聚合根决定，持久化时由转换器写入根上的申请编号。
 * 同一申请同一时间最多一个未关闭问题件，由聚合根与数据库唯一约束共同保证。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
public class ClaimApplicationIssue {

    /** 数据库主键，仅用于持久化更新条件。 */
    private Long id;

    /** 平台问题件编号，由审核系统生成，全局唯一。 */
    private String issueNo;

    /** 问题件原因说明，必填，最大 512 字符。 */
    private String reason;

    /** 问题件下发人标识，必填，最大 128 字符，落库后不可变。 */
    private String raisedBy;

    /** 问题件关闭人标识，未关闭时为空，最大 128 字符。 */
    private String closedBy;

    /** 问题件详情 JSON，记录缺少的材料清单、疑问说明等。 */
    private String issueContent;

    /** 问题件处理状态。 */
    private ClaimApplicationIssueStatus status;

    /** 问题件关闭时间，未关闭时为空。 */
    private LocalDateTime closedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /**
     * 创建新问题件，状态初始化为 OPEN。
     *
     * @param issueNo      问题件编号
     * @param reason       问题原因
     * @param raisedBy     问题件下发人
     * @param issueContent 问题详情 JSON
     * @return 新问题件
     */
    public static ClaimApplicationIssue create(String issueNo, String reason, String raisedBy, String issueContent) {
        AssertUtil.notBlank(issueNo, "问题件编号不能为空");
        AssertUtil.notBlank(reason, "问题原因不能为空");
        AssertUtil.isTrue(reason.length() <= 512, "问题原因不能超过 512 字符");
        AssertUtil.notBlank(raisedBy, "下发人不能为空");
        AssertUtil.isTrue(raisedBy.length() <= 128, "下发人不能超过 128 字符");
        AssertUtil.notNull(issueContent, "问题详情不能为空");
        return ClaimApplicationIssue.builder()
                .issueNo(issueNo)
                .reason(reason)
                .raisedBy(raisedBy)
                .issueContent(issueContent)
                .status(ClaimApplicationIssueStatus.OPEN)
                .build();
    }

    /**
     * 从已落库的数据重建问题件实体，仅供持久化层反序列化使用。
     *
     * @param id           数据库主键
     * @param issueNo      问题件编号
     * @param reason       问题原因
     * @param raisedBy     问题件下发人
     * @param closedBy     问题件关闭人，未关闭时为空
     * @param issueContent 问题详情 JSON
     * @param status       问题件状态
     * @param closedAt     关闭时间
     * @param createdAt    创建时间
     * @param updatedAt    更新时间
     * @return 已落库的问题件
     */
    public static ClaimApplicationIssue restore(Long id, String issueNo, String reason, String raisedBy,
                                                String closedBy, String issueContent,
                                                ClaimApplicationIssueStatus status, LocalDateTime closedAt,
                                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        return ClaimApplicationIssue.builder()
                .id(id)
                .issueNo(issueNo)
                .reason(reason)
                .raisedBy(raisedBy)
                .closedBy(closedBy)
                .issueContent(issueContent)
                .status(status)
                .closedAt(closedAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * 关闭问题件，记录关闭人与关闭时间；下发人保持不变。
     *
     * @param closedBy 问题件关闭人
     */
    public void close(String closedBy) {
        if (status != ClaimApplicationIssueStatus.OPEN) {
            throw new DomainException(ClaimApplicationIssueErrorCode.ISSUE_STATUS_INVALID,
                    "问题件状态不允许关闭，issueNo=" + issueNo + "，当前状态=" + status);
        }
        AssertUtil.notBlank(closedBy, "关闭人不能为空");
        AssertUtil.isTrue(closedBy.length() <= 128, "关闭人不能超过 128 字符");
        this.status = ClaimApplicationIssueStatus.CLOSED;
        this.closedBy = closedBy;
        this.closedAt = LocalDateTime.now();
    }

    /**
     * 判断问题件是否未关闭。
     *
     * @return 状态为 OPEN 时为 true
     */
    public boolean isOpen() {
        return status == ClaimApplicationIssueStatus.OPEN;
    }

    /**
     * 判断问题件是否已关闭。
     *
     * @return 状态为 CLOSED 时为 true
     */
    public boolean isClosed() {
        return status == ClaimApplicationIssueStatus.CLOSED;
    }
}
