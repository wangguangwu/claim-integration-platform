package com.wangguangwu.claim.application.usecase.intake;

/**
 * 存在性判断与库中实际记录不一致时的用例内部重试信号。
 *
 * <p>它不表达业务拒绝，也不对外暴露错误码，因此不放进 {@code domain/claim/exception}；
 * 签收入口捕获后按"该外部申请号尚未存在"重新准备编号并重试。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
class ApplicationExistenceMismatchException extends RuntimeException {

    ApplicationExistenceMismatchException() {
        super("理赔申请存在性判断与库中记录不一致，需要重新签收");
    }
}
