package com.wangguangwu.claim.application.usecase.intake.result;

/**
 * 理赔申请签收结果。
 *
 * @param applicationNo 平台申请编号
 * @param disposition 签收结果类型
 * @author wangguangwu
 * @since 2026-10-27
 */
public record ClaimApplicationIntakeResult(String applicationNo, ClaimApplicationIntakeDisposition disposition) {

    /**
     * 判断本次请求是否改变了业务数据。
     *
     * @return 创建或重新申请时为 true
     */
    public boolean changed() {
        return disposition != ClaimApplicationIntakeDisposition.REUSED;
    }
}
