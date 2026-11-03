package com.wangguangwu.claim.application.infrastructure.integration.config;

/**
 * 本地审核签收查询的返回结果，仅用于联调配置。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public enum AuditSignedQueryResult {

    /** 审核系统仍在处理。 */
    PROCESSING,

    /** 审核系统已完成签收，需配合案件号配置使用。 */
    SIGNED,

    /** 审核系统未找到对应申请。 */
    NOT_FOUND
}
