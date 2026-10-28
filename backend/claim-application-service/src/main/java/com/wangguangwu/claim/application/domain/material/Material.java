package com.wangguangwu.claim.application.domain.material;

import lombok.Getter;
import lombok.Setter;

/**
 * 理赔申请材料，记录租户初始地址及后续上传地址。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Getter
@Setter
public class Material {

    private Long id;
    private String applicationNo;
    private String batchNo;
    private String materialType;
    private String originalFileUrl;
    private String uploadedFileUrl;
    private MaterialStatus status;
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
    public static Material createPending(String applicationNo, String batchNo, String materialType,
                                         String originalFileUrl) {
        Material material = new Material();
        material.applicationNo = applicationNo;
        material.batchNo = batchNo;
        material.materialType = materialType;
        material.originalFileUrl = originalFileUrl;
        material.status = MaterialStatus.PENDING_PROCESS;
        return material;
    }
}
