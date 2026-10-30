package com.wangguangwu.claim.application.domain.claim;

/**
 * 签收策略端口，唯一键冲突重试上限由部署配置决定。
 *
 * @author wangguangwu
 * @since 2026-10-30
 */
public interface ClaimApplicationIntakePolicy {

    /**
     * 同一外部申请号唯一键冲突后的最大重试次数。
     *
     * @return 重试次数上限
     */
    int uniqueKeyRetryTimes();
}
