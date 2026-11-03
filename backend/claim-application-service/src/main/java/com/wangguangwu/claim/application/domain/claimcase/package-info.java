/**
 * 理赔申请与审核案件的关联事实。
 *
 * <p>案件生命周期由案件服务承载，本域只记录审核系统签收后返回的案件号，
 * 因此与 {@code ClaimApplication} 聚合分开，单独维护追加式事实。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
package com.wangguangwu.claim.application.domain.claimcase;
