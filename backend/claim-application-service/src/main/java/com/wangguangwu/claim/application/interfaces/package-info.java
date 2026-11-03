/**
 * 理赔申请服务的入站适配层。
 *
 * <p>按协议类型拆分：{@code web} 承接 HTTP 与租户协议，{@code consumer} 承接 MQ 消费，{@code dto} 存放请求与响应契约，
 * {@code scheduler} 承接定时触发。本层只做协议适配与转换，业务处理委托给 {@code application} 层。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.interfaces;
