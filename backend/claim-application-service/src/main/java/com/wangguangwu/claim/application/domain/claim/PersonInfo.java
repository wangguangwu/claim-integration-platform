package com.wangguangwu.claim.application.domain.claim;

/**
 * 平台标准人员信息，承载租户协议归一化后的四个基础字段。
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
