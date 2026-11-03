package com.wangguangwu.claim.application.shared.exception;

/**
 * 可对外返回的业务错误码契约。
 *
 * <p>错误码按业务域各自定义枚举，本接口只暴露对外字符串，不绑定 HTTP 状态，
 * 状态映射由入站适配层统一负责。</p>
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
public interface BusinessErrorCode {

    /**
     * 返回对外错误码字符串。
     *
     * @return 对外错误码
     */
    String code();
}
