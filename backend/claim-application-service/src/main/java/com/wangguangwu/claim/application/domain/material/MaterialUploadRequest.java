package com.wangguangwu.claim.application.domain.material;

/**
 * 材料文件上传请求。
 *
 * @param claimApplicationNo 平台申请编号
 * @param batchNo       材料批次编号
 * @param materialType  材料类型
 * @param sourceUrl     租户提交的初始文件地址
 * @author wangguangwu
 * @since 2026-10-27
 */
public record MaterialUploadRequest(String claimApplicationNo, String batchNo, String materialType, String sourceUrl) {
}
