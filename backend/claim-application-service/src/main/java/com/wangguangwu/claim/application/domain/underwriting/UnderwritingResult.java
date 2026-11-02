package com.wangguangwu.claim.application.domain.underwriting;

/**
 * 自动承保处理结果。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum UnderwritingResult {

    /** 承保系统仍在处理，当前不推进申请状态。 */
    PROCESSING,

    /** 承保系统已处理完成，申请可推进到等待审核。 */
    COMPLETED,

    /** 承保系统未找到对应的承保任务。 */
    NOT_FOUND
}
