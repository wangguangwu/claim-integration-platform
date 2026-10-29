package com.wangguangwu.claim.application.infrastructure.material.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 理赔材料数据对象，对应 {@code t_claim_application_material} 表。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
@TableName("t_claim_application_material")
public class MaterialDO {

    /** 材料记录自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属理赔申请的平台申请编号。 */
    @TableField("application_no")
    private String applicationNo;

    /** 同次申请提交共用的材料批次编号。 */
    @TableField("batch_no")
    private String batchNo;

    /** 归一化后的材料类型。 */
    @TableField("material_type")
    private String materialType;

    /** 租户提交的原始文件地址。 */
    @TableField("original_file_url")
    private String originalFileUrl;

    /** 文件处理完成后的上传或转存地址。 */
    @TableField("uploaded_file_url")
    private String uploadedFileUrl;

    /** 材料处理状态枚举名称。 */
    private String status;

    /** 是否已被重新申请流程逻辑删除。 */
    private Boolean deleted;

    /** 逻辑删除发生时间；未删除时为空。 */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /** 数据创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 数据最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
