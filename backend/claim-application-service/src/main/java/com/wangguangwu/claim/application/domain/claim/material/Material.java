package com.wangguangwu.claim.application.domain.claim.material;

import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import com.wangguangwu.claim.application.domain.claim.exception.ClaimApplicationErrorCode;
import com.wangguangwu.claim.application.domain.exception.DomainException;
import com.wangguangwu.claim.foundation.common.util.AssertUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 理赔申请内的材料实体，记录租户初始地址及后续上传地址。
 *
 * <p>材料不属于独立聚合，不持有平台申请编号；归属由聚合根决定，持久化时由转换器写入根上的申请编号。
 * 字段装配只通过本类的领域工厂完成，处理状态由本域的迁移方法决定。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
public class Material {

    /** 数据库主键，仅用于持久化更新条件。 */
    private Long id;

    /** 同一次申请提交生成的材料批次编号。 */
    private String batchNo;

    /** 租户协议归一化后的材料类型。 */
    private String materialType;

    /** 租户提交时提供的原始文件地址。 */
    private String originalFileUrl;

    /** 文件处理成功后生成的上传或转存地址。 */
    private String uploadedFileUrl;

    /** 当前材料处理状态。 */
    private MaterialStatus status;

    /** 是否已被重新申请或租户补材逻辑删除。 */
    private boolean deleted;

    /** 逻辑删除发生时间；未删除时为空。 */
    private LocalDateTime deletedAt;

    /**
     * 把申请随单提交的一份材料转成待处理材料，材料状态由本域决定。
     *
     * @param batchNo    本次提交共用的材料批次编号
     * @param submission 申请提交的材料
     * @return 待处理材料
     */
    public static Material createPending(String batchNo, MaterialSubmission submission) {
        AssertUtil.notBlank(batchNo, "材料批次编号不能为空");
        return Material.builder().batchNo(batchNo).materialType(submission.materialType())
                .originalFileUrl(submission.originalFileUrl()).status(MaterialStatus.PENDING_PROCESS).build();
    }

    /**
     * 从已落库的数据重建材料实体，仅供持久化层反水化使用。
     *
     * @param id              数据库主键
     * @param batchNo         材料批次编号
     * @param materialType    材料类型
     * @param originalFileUrl 租户提交的初始文件地址
     * @param uploadedFileUrl 已写入的处理结果地址
     * @param status          材料处理状态
     * @param deleted         是否已逻辑删除
     * @param deletedAt       逻辑删除时间
     * @return 已落库的材料
     */
    public static Material restore(Long id, String batchNo, String materialType, String originalFileUrl,
                                   String uploadedFileUrl, MaterialStatus status, boolean deleted,
                                   LocalDateTime deletedAt) {
        return Material.builder().id(id).batchNo(batchNo).materialType(materialType)
                .originalFileUrl(originalFileUrl).uploadedFileUrl(uploadedFileUrl).status(status).deleted(deleted)
                .deletedAt(deletedAt).build();
    }

    /**
     * 写入文件处理结果，仅待处理材料允许迁移状态。
     *
     * @param succeeded       是否处理成功
     * @param uploadedFileUrl 处理成功后的文件地址
     */
    public void applyProcessResult(boolean succeeded, String uploadedFileUrl) {
        if (status != MaterialStatus.PENDING_PROCESS) {
            throw new DomainException(ClaimApplicationErrorCode.CLAIM_APPLICATION_MATERIAL_STATUS_INVALID,
                    "仅待处理材料允许写入处理结果，batchNo=" + batchNo + "，materialType=" + materialType
                            + "，当前状态=" + status);
        }
        if (succeeded) {
            AssertUtil.notBlank(uploadedFileUrl, "材料处理成功必须携带上传地址，materialType=" + materialType);
            this.uploadedFileUrl = uploadedFileUrl;
            this.status = MaterialStatus.PROCESSED;
            return;
        }
        this.status = MaterialStatus.FAILED;
    }

    /**
     * 逻辑删除材料，重新申请与租户补材时保留历史行供追溯。
     */
    public void softDelete() {
        if (deleted) {
            return;
        }
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
