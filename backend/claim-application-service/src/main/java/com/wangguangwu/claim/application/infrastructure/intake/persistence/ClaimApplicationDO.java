package com.wangguangwu.claim.application.infrastructure.intake.persistence;

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
 * 理赔申请主表数据对象，对应 {@code t_claim_application} 表。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_claim_application")
public class ClaimApplicationDO {

    /** 主表自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 理赔申请所属租户编码。 */
    @TableField("tenant_code")
    private String tenantCode;

    /** 平台全局唯一理赔申请编号。 */
    @TableField("claim_application_no")
    private String claimApplicationNo;

    /** 租户侧申请编号，与租户编码组成幂等键。 */
    @TableField("external_claim_application_no")
    private String externalClaimApplicationNo;

    /** 理赔申请生命周期状态枚举名称。 */
    private String status;

    /** 同一外部申请编号的受理尝试次数。 */
    @TableField("claim_application_attempt")
    private Integer claimApplicationAttempt;

    /** 申请人标准化信息 JSON。 */
    @TableField("applicant_json")
    private String applicantJson;

    /** 主被保险人标准化信息 JSON。 */
    @TableField("primary_insured_json")
    private String primaryInsuredJson;

    /** 出险人标准化信息 JSON。 */
    @TableField("accident_person_json")
    private String accidentPersonJson;

    /** 领款人标准化信息 JSON。 */
    @TableField("payee_json")
    private String payeeJson;

    /** 数据创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 数据最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
