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
 * 案件关联数据对象，对应 {@code t_claim_case_relation} 表。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_claim_case_relation")
public class ClaimCaseRelationDO {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 理赔申请所属租户编码。 */
    @TableField("tenant_code")
    private String tenantCode;

    /** 关联理赔申请的平台申请编号。 */
    @TableField("claim_application_no")
    private String claimApplicationNo;

    /** 审核签收返回的案件编号。 */
    @TableField("case_no")
    private String caseNo;

    /** 数据创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 数据最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
