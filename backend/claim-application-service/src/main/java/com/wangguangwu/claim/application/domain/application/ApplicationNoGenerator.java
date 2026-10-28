package com.wangguangwu.claim.application.domain.application;

/**
 * 平台理赔申请编号生成端口。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface ApplicationNoGenerator {

    /**
     * 生成全局唯一的 Application 编号。
     *
     * @return Application 编号
     */
    String nextApplicationNo();

    /**
     * 将当日计数器与已落库的最大申请编号对齐。
     *
     * <p>Redis 重启恢复到旧快照或故障期间由 MySQL 兜底发号后，计数器可能落后于数据库，
     * 需要在唯一键冲突后重新校准，避免持续生成已占用的编号。</p>
     */
    void synchronizeDailyCounter();
}
