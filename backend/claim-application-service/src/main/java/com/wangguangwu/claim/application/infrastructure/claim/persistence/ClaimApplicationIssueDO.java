package com.wangguangwu.claim.application.infrastructure.claim.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 问题件数据对象，对应 {@code t_claim_application_issue} 表。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_claim_application_issue")
public class ClaimApplicationIssueDO {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属理赔申请的平台申请编号。 */
    @TableField("claim_application_no")
    private String claimApplicationNo;

    /** 平台问题件编号，由审核系统生成，全局唯一。 */
    @TableField("issue_no")
    private String issueNo;

    /** 问题件原因说明。 */
    @TableField("reason")
    private String reason;

    /** 创建或处理问题件的审核人员标识。 */
    @TableField("auditor")
    private String auditor;

    /** 问题件详情 JSON。 */
    @TableField("issue_content_json")
    private String issueContentJson;

    /** 问题件处理状态枚举名称。 */
    @TableField("status")
    private String status;

    /** 是否为未关闭问题件，1 表示 OPEN，0 表示 CLOSED。 */
    @TableField("is_open")
    private Integer isOpen;

    /** 问题件关闭时间，未关闭时为空。 */
    @TableField("closed_at")
    private LocalDateTime closedAt;

    /** 数据创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 数据最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
