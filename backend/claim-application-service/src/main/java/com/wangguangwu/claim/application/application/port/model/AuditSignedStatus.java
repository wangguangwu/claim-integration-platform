package com.wangguangwu.claim.application.application.port.model;

import java.util.List;

/**
 * 审核签收状态查询结果，用于判断等待审核滞留申请的下一步补偿动作。
 *
 * <p>三种结果互斥：已签收携带案件号，处理中表示审核系统尚未完成，未找到表示审核交付可能丢失。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public sealed interface AuditSignedStatus {

    /**
     * 审核系统已完成签收。
     *
     * @param caseNos 审核系统返回的案件号，构造时复制为不可变列表
     */
    record Signed(List<String> caseNos) implements AuditSignedStatus {

        /**
         * 复制案件号列表，调用方修改原列表不影响查询结果。
         */
        public Signed {
            caseNos = caseNos == null ? List.of() : List.copyOf(caseNos);
        }
    }

    /**
     * 审核系统仍在处理，当前不推进申请状态。
     */
    record Processing() implements AuditSignedStatus {
    }

    /**
     * 审核系统没有找到对应申请，通常表示审核交付未到达。
     */
    record NotFound() implements AuditSignedStatus {
    }
}
