/**
 * 定时调度入站适配。
 *
 * <p>触发 Outbox 投递与滞留补偿任务；调度只负责触发，业务判断由应用用例完成。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.interfaces.scheduler;
