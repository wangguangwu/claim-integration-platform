package com.wangguangwu.claim.application.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 理赔材料数据对象，对应 {@code t_material} 表。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
@TableName("t_material")
public class MaterialDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("application_no")
    private String applicationNo;

    @TableField("batch_no")
    private String batchNo;

    @TableField("material_type")
    private String materialType;

    @TableField("original_file_url")
    private String originalFileUrl;

    @TableField("uploaded_file_url")
    private String uploadedFileUrl;

    private String status;
    private Boolean deleted;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
