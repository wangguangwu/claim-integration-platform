package com.wangguangwu.claim.application.application.query.model;

import com.wangguangwu.claim.application.domain.claim.MaterialStatus;
import java.time.LocalDateTime;

/**
 * 理赔材料查询视图，只包含未删除材料。
 *
 * @param batchNo        材料批次编号
 * @param materialType   材料类型
 * @param originalFileUrl 租户提交的原始文件地址
 * @param uploadedFileUrl 处理完成后的文件地址
 * @param status         材料处理状态
 * @param createdAt      创建时间
 * @author wangguangwu
 * @since 2026-11-03
 */
public record MaterialView(String batchNo, String materialType, String originalFileUrl, String uploadedFileUrl,
                           MaterialStatus status, LocalDateTime createdAt) {
}
