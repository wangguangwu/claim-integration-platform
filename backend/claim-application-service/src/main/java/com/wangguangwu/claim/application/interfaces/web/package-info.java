/**
 * HTTP 入站适配。
 *
 * <p>按角色分为 {@code controller}（HTTP 入口）、{@code advice}（统一异常渲染）、
 * {@code dto}（HTTP 请求与响应）与 {@code tenantprotocol}（租户报文协议适配）；
 * 协议报文转换为应用命令后交给用例编排。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
package com.wangguangwu.claim.application.interfaces.web;
