/**
 * 理赔申请聚合内的问题件实体。
 *
 * <p>问题件承载审核侧下发与关闭的业务事实，打开/关闭只能由聚合根发起；问题件不单独提供仓储，
 * 与申请状态在同一事务内落库。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
package com.wangguangwu.claim.application.domain.claim.issue;
