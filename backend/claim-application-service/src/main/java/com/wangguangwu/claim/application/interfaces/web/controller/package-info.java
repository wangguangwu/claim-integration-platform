/**
 * HTTP 入口。
 *
 * <p>只读取请求上下文、调用应用用例并组装统一响应外壳，不承载业务编排、事务与持久化；
 * 入站报文转换交给 {@code tenantprotocol}，异常渲染交给 {@code advice}。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
package com.wangguangwu.claim.application.interfaces.web.controller;
