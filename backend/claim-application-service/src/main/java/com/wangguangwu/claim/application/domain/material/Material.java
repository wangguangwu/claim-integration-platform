package com.wangguangwu.claim.application.domain.material;

import com.wangguangwu.claim.application.domain.claim.MaterialSubmission;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 理赔申请材料，记录租户初始地址及后续上传地址。
 *
 * <p>字段装配只通过本类的领域工厂完成，材料状态由本域的迁移方法决定。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
public class Material {

    /** 数据库主键，仅用于持久化更新条件。 */
    private Long id;

    /** 所属理赔申请的平台申请编号。 */
    private String applicationNo;

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

    /** 是否为重新申请时逻辑删除的历史材料。 */
    private boolean deleted;

    /**
     * 创建待处理的首次或补充材料。
     *
     * @param applicationNo   平台申请编号
     * @param batchNo         材料批次编号
     * @param materialType    材料类型
     * @param originalFileUrl 租户提交的初始文件地址
     * @return 待处理材料
     */
    private static Material createPending(String applicationNo, String batchNo, String materialType,
                                         String originalFileUrl) {
        return Material.builder().applicationNo(applicationNo).batchNo(batchNo).materialType(materialType)
                .originalFileUrl(originalFileUrl).status(MaterialStatus.PENDING_PROCESS).build();
    }

    /**
     * 把理赔申请随单提交的一份材料转成待处理材料，材料状态由本域决定。
     *
     * @param applicationNo 平台申请编号
     * @param batchNo       本次提交共用的材料批次编号
     * @param submission    申请提交的材料
     * @return 待处理材料
     */
    public static Material createPending(String applicationNo, String batchNo, MaterialSubmission submission) {
        return createPending(applicationNo, batchNo, submission.materialType(), submission.originalFileUrl());
    }

    /**
     * 从已落库的数据重建材料聚合，仅供持久化层反水化使用。
     *
     * @param id              数据库主键
     * @param applicationNo   平台申请编号
     * @param batchNo         材料批次编号
     * @param materialType    材料类型
     * @param originalFileUrl 租户提交的初始文件地址
     * @param uploadedFileUrl 已写入的处理结果地址
     * @param status          材料处理状态
     * @param deleted         是否为重新申请后的历史材料
     * @return 已落库的材料
     */
    public static Material restore(Long id, String applicationNo, String batchNo, String materialType,
                                   String originalFileUrl, String uploadedFileUrl, MaterialStatus status,
                                   boolean deleted) {
        return Material.builder().id(id).applicationNo(applicationNo).batchNo(batchNo).materialType(materialType)
                .originalFileUrl(originalFileUrl).uploadedFileUrl(uploadedFileUrl).status(status).deleted(deleted)
                .build();
    }

    /**
     * 记录文件上传成功结果。
     *
     * @param uploadedFileUrl 上传后文件地址
     */
    public void markProcessed(String uploadedFileUrl) {
        if (status != MaterialStatus.PENDING_PROCESS) {
            throw new IllegalStateException("仅待处理材料允许写入处理结果，applicationNo=" + applicationNo
                    + "，materialType=" + materialType);
        }
        this.uploadedFileUrl = uploadedFileUrl;
        this.status = MaterialStatus.PROCESSED;
    }

    /**
     * 记录文件处理失败结果，材料保留原始地址供追溯。
     */
    public void markFailed() {
        if (status != MaterialStatus.PENDING_PROCESS) {
            throw new IllegalStateException("仅待处理材料允许写入处理结果，applicationNo=" + applicationNo
                    + "，materialType=" + materialType);
        }
        this.status = MaterialStatus.FAILED;
    }
}
