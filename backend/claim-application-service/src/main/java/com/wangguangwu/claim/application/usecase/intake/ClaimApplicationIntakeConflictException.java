package com.wangguangwu.claim.application.usecase.intake;

/**
 * 签收保存失败的内部冲突信号，只用于触发事务回滚并由用例在新事务中重新分类。
 *
 * <p>该信号不是对外业务错误：事务服务抛出它让本次事务整体回滚，用例捕获后在保存事务之外
 * 重读聚合，再决定按复用、状态冲突还是处理中对外返回。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
class ClaimApplicationIntakeConflictException extends RuntimeException {

    private final String claimApplicationNo;

    private final int attemptBefore;

    private final boolean supplement;

    /**
     * 创建保存冲突信号。
     *
     * @param claimApplicationNo 本次保存的平台申请编号
     * @param attemptBefore      调用聚合变更前的受理尝试次数
     * @param supplement         本次是否为租户补材
     */
    ClaimApplicationIntakeConflictException(String claimApplicationNo, int attemptBefore, boolean supplement) {
        super("理赔申请保存冲突，claimApplicationNo=" + claimApplicationNo + "，attemptBefore=" + attemptBefore
                + "，supplement=" + supplement);
        this.claimApplicationNo = claimApplicationNo;
        this.attemptBefore = attemptBefore;
        this.supplement = supplement;
    }

    String claimApplicationNo() {
        return claimApplicationNo;
    }

    int attemptBefore() {
        return attemptBefore;
    }

    boolean supplement() {
        return supplement;
    }
}
