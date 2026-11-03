package com.wangguangwu.claim.application.interfaces.web;

import com.wangguangwu.claim.application.shared.exception.BusinessErrorCode;

/**
 * HTTP 入口控制器的错误码声明契约。
 *
 * <p>请求体字段校验失败、报文不可解析与未预期异常发生在业务编排之前，用例无法给出业务错误码，
 * 因此由当前控制器声明本接口的“请求非法”码与“系统错误”码，全局异常处理器据此组装失败响应。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public interface ApiErrorCodeProvider {

    /**
     * 返回本接口在请求报文非法时使用的错误码。
     *
     * @return 请求非法错误码
     */
    BusinessErrorCode requestInvalidCode();

    /**
     * 返回本接口在未预期异常时使用的系统错误码。
     *
     * @return 系统错误码
     */
    BusinessErrorCode systemErrorCode();
}
