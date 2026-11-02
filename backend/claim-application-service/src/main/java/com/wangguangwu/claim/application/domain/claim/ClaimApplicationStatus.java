package com.wangguangwu.claim.application.domain.claim;

/**
 * 理赔申请生命周期状态，覆盖接入、审核与问题件链路。
 *
 * <p>结案与撤销状态属于后续审核能力，未实现前不保留枚举值。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum ClaimApplicationStatus {

    /** 已接收签收请求，尚未完成受理前置校验。 */
    PENDING_ACCEPT,

    /** 材料处理成功，申请已受理并可进入自动承保。 */
    ACCEPTED,

    /** 材料处理存在失败，申请可在修正材料后重新申请。 */
    ACCEPT_FAILED,

    /** 已发起自动承保，等待承保系统返回处理完成通知。 */
    UNDERWRITING,

    /** 自动承保完成，等待交付审核系统。 */
    WAIT_AUDIT,

    /** 审核退回补正，存在未关闭的问题件。 */
    ISSUE
}
