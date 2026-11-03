package com.wangguangwu.claim.application.usecase.audit.port;

import com.wangguangwu.claim.application.usecase.audit.model.AuditSignedStatus;

/**
 * 审核签收状态查询端口，供等待审核滞留补偿判断签收结果。
 *
 * <p>审核系统不属于本项目实现范围，基础设施层以进程内实现按配置返回；
 * 查询不可用或失败时抛出异常，由补偿保持原状态等待下一轮。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public interface AuditSignedStatusQuery {

    /**
     * 查询指定申请的审核签收状态。
     *
     * @param claimApplicationNo 平台申请编号
     * @return 审核签收状态
     */
    AuditSignedStatus query(String claimApplicationNo);
}
