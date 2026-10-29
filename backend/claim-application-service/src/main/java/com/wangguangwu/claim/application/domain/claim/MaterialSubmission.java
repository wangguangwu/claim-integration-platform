package com.wangguangwu.claim.application.domain.claim;

/**
 * 理赔申请随单提交的材料，只承载协议归一化后的材料类型与初始文件地址。
 *
 * <p>它是理赔申请提交内容的一部分，因此归属 {@code claim} 聚合；转换成材料聚合由材料域的工厂完成，
 * 本值对象不参与材料状态的拼装。</p>
 *
 * @param materialType    材料类型
 * @param originalFileUrl 租户提交的文件地址
 * @author wangguangwu
 * @since 2026-10-27
 */
public record MaterialSubmission(String materialType, String originalFileUrl) {
}
