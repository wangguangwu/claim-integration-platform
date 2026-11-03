package com.wangguangwu.claim.application.usecase.audit;

/**
 * 审核签收保存时的乐观锁冲突信号。
 *
 * <p>仅用于触发事务回滚与消息重投，不映射 HTTP 状态，也不写入消费登记；
 * 重试时会重新读取聚合，按最新状态继续处理。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public class AuditSignedConflictException extends RuntimeException {

    /**
     * 创建审核签收冲突异常。
     *
     * @param claimApplicationNo 平台申请编号
     * @param eventId            审核签收事件标识
     */
    public AuditSignedConflictException(String claimApplicationNo, String eventId) {
        super("审核签收保存发生版本冲突，claimApplicationNo=" + claimApplicationNo + "，eventId=" + eventId);
    }
}
