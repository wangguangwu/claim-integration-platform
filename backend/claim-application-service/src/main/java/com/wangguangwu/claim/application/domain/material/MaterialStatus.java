package com.wangguangwu.claim.application.domain.material;

/**
 * 理赔材料处理状态。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public enum MaterialStatus {

    /** 材料已接收，等待异步上传或文件处理。 */
    PENDING_PROCESS,

    /** 材料已成功处理并取得可用文件地址。 */
    PROCESSED,

    /** 材料处理失败，需要根据失败原因重新提交或补正。 */
    FAILED
}
