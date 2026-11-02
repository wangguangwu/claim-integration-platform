package com.wangguangwu.claim.application.shared.contract.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 材料处理载荷，只保留消费方实际读取的申请尝试次数。
 *
 * @param claimApplicationAttempt 事件产生时的申请尝试次数
 * @author wangguangwu
 * @since 2026-11-02
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaterialProcessPayload(Integer claimApplicationAttempt) {
}
