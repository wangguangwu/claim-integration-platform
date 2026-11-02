package com.wangguangwu.claim.application.usecase.intake.port;

/**
 * 签收应用控制策略端口，唯一键冲突重试上限由部署配置决定。
 *
 * <p>本端口只承载应用层控制参数，不表达“申请是否允许受理”等业务规则；
 * 业务规则属于领域模型，不得下沉到用例层。</p>
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
