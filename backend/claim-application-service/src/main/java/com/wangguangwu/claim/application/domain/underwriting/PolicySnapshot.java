package com.wangguangwu.claim.application.domain.underwriting;

/**
 * 有效保单快照，仅保留业务判断需要的非敏感字段。
 *
 * @param policyNo 保单号
 * @author wangguangwu
 * @since 2026-10-27
 */
public record PolicySnapshot(String policyNo) {
}
