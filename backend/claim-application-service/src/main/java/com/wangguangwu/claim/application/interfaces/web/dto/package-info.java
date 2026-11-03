/**
 * HTTP 入站请求与响应契约。
 *
 * <p>只服务 {@code web} 入口，请求记录位于 {@code request} 子包，统一响应外壳与成功响应位于
 * {@code response} 子包；不包含业务规则与持久化细节，不向其他入口或领域层外泄。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.interfaces.web.dto;
