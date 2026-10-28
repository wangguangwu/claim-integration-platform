package com.wangguangwu.claim.application.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Application 主表数据对象，对应 {@code t_claim_application} 表。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
@TableName("t_claim_application")
public class ApplicationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_code")
    private String tenantCode;

    @TableField("application_no")
    private String applicationNo;

    @TableField("external_application_no")
    private String externalApplicationNo;

    private String status;

    @TableField("application_attempt")
    private Integer applicationAttempt;

    @TableField("applicant_json")
    private String applicantJson;

    @TableField("primary_insured_json")
    private String primaryInsuredJson;

    @TableField("accident_person_json")
    private String accidentPersonJson;

    @TableField("payee_json")
    private String payeeJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
