/**
 * 理赔申请服务的入站适配层。
 *
 * <p>承接 HTTP 请求、消息消费和定时调度，完成协议解析、参数校验、命令转换、统一 HTTP 响应和响应映射；
 * 不承载领域状态流转或持久化细节，业务处理委托给应用用例层。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.interfaces;
