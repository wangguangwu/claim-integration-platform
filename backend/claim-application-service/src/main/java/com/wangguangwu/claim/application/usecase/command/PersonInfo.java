package com.wangguangwu.claim.application.usecase.command;

/**
 * 租户协议转换后的基础人员信息。
 *
 * @param name            姓名
 * @param gender          性别
 * @param certificateType 证件类型
 * @param certificateNo   证件号码
 * @author wangguangwu
 * @since 2026-10-27
 */
public record PersonInfo(String name, Gender gender, String certificateType, String certificateNo) {
}
