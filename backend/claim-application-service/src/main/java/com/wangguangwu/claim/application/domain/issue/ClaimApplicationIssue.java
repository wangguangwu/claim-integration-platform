package com.wangguangwu.claim.application.domain.issue;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 问题件聚合根，封装审核系统下发的问题件生命周期。
 *
 * <p>问题件表示理赔申请材料不完整或存在疑问，需要租户补充材料后重新提交审核。
 * 同一申请同一时间最多一个未关闭问题件，由数据库唯一约束兜底。</p>
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
public class ClaimApplicationIssue {

    /** 数据库主键，仅用于持久化更新条件。 */
    private Long id;

    /** 所属理赔申请的平台申请编号。 */
    private String claimApplicationNo;

    /** 平台问题件编号，由审核系统生成，全局唯一。 */
    private String issueNo;

    /** 问题件原因说明，必填，最大 512 字符。 */
    private String reason;

    /** 创建或处理问题件的审核人员标识，必填，最大 128 字符。 */
    private String auditor;

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
     * @param claimApplicationNo 平台申请编号
     * @param issueNo            问题件编号
     * @param reason             问题原因
     * @param auditor            审核人员
     * @param issueContent       问题详情 JSON
     * @return 新问题件
     */
    public static ClaimApplicationIssue create(String claimApplicationNo, String issueNo, String reason,
                                               String auditor, String issueContent) {
        if (claimApplicationNo == null || claimApplicationNo.isBlank()) {
            throw new IllegalArgumentException("平台申请编号不能为空");
        }
        if (issueNo == null || issueNo.isBlank()) {
            throw new IllegalArgumentException("问题件编号不能为空");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("问题原因不能为空");
        }
        if (reason.length() > 512) {
            throw new IllegalArgumentException("问题原因不能超过 512 字符");
        }
        if (auditor == null || auditor.isBlank()) {
            throw new IllegalArgumentException("审核人员不能为空");
        }
        if (auditor.length() > 128) {
            throw new IllegalArgumentException("审核人员不能超过 128 字符");
        }
        if (issueContent == null) {
            throw new IllegalArgumentException("问题详情不能为空");
        }
        return ClaimApplicationIssue.builder()
                .claimApplicationNo(claimApplicationNo)
                .issueNo(issueNo)
                .reason(reason)
                .auditor(auditor)
                .issueContent(issueContent)
                .status(ClaimApplicationIssueStatus.OPEN)
                .build();
    }

    /**
     * 从已落库的数据重建聚合，仅供持久化层反序列化使用。
     *
     * @param id                  数据库主键
     * @param claimApplicationNo  平台申请编号
     * @param issueNo             问题件编号
     * @param reason              问题原因
     * @param auditor             审核人员
     * @param issueContent        问题详情 JSON
     * @param status              问题件状态
     * @param closedAt            关闭时间
     * @param createdAt           创建时间
     * @param updatedAt           更新时间
     * @return 已落库的问题件
     */
    public static ClaimApplicationIssue restore(Long id, String claimApplicationNo, String issueNo, String reason,
                                                String auditor, String issueContent, ClaimApplicationIssueStatus status,
                                                LocalDateTime closedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return ClaimApplicationIssue.builder()
                .id(id)
                .claimApplicationNo(claimApplicationNo)
                .issueNo(issueNo)
                .reason(reason)
                .auditor(auditor)
                .issueContent(issueContent)
                .status(status)
                .closedAt(closedAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * 关闭问题件，记录关闭时间。
     *
     * @param auditor 处理人
     */
    public void close(String auditor) {
        if (status != ClaimApplicationIssueStatus.OPEN) {
            throw new IllegalStateException("问题件状态不允许关闭，issueNo=" + issueNo + "，当前状态=" + status);
        }
        if (auditor == null || auditor.isBlank()) {
            throw new IllegalArgumentException("处理人不能为空");
        }
        this.status = ClaimApplicationIssueStatus.CLOSED;
        this.auditor = auditor;
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
