/**
 * 理赔申请聚合内的材料实体。
 *
 * <p>材料记录本次受理的提交快照与处理状态，状态迁移只能由聚合根发起；材料不单独提供仓储，
 * 与申请状态在同一事务内落库。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
package com.wangguangwu.claim.application.domain.claim.material;
