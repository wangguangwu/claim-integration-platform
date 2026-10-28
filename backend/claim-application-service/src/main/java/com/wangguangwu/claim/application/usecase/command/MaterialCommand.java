package com.wangguangwu.claim.application.usecase.command;

/**
 * 标准化材料命令，保留租户原始文件地址。
 *
 * @param materialType    材料类型
 * @param originalFileUrl 租户提交的文件地址
 * @author wangguangwu
 * @since 2026-10-27
 */
public record MaterialCommand(String materialType, String originalFileUrl) {
}
