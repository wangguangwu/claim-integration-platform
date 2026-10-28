package com.wangguangwu.claim.application.usecase;

/**
 * 存在缓存与实际数据不一致时，请求在事务外重新准备编号后重试。
 *
 * <p>仅用于用例内部重试控制，不对外暴露业务错误。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class ApplicationIntakeCacheStaleException extends RuntimeException {

    ApplicationIntakeCacheStaleException() {
        super("存在缓存与数据库不一致，需要重新签收");
    }
}
