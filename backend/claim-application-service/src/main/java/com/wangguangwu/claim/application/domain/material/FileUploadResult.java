package com.wangguangwu.claim.application.domain.material;

/**
 * 材料文件上传结果。
 *
 * <p>{@code success=false} 表示文件系统明确拒绝了该材料，属于业务失败；
 * 文件系统不可用或超时以异常形式抛出，由调用方保留原状态等待重试，不能转成业务失败。</p>
 *
 * @param success         是否成功
 * @param uploadedFileUrl 上传后文件地址，仅成功时有值
 * @param errorCode       失败错误码，仅失败时有值
 * @param errorMessage    失败说明，仅失败时有值
 * @author wangguangwu
 * @since 2026-10-27
 */
public record FileUploadResult(boolean success, String uploadedFileUrl, String errorCode, String errorMessage) {

    /**
     * 构建上传成功结果。
     *
     * @param uploadedFileUrl 上传后文件地址
     * @return 成功结果
     */
    public static FileUploadResult succeeded(String uploadedFileUrl) {
        return new FileUploadResult(true, uploadedFileUrl, null, null);
    }

    /**
     * 构建文件系统明确拒绝的结果。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误说明
     * @return 失败结果
     */
    public static FileUploadResult failed(String errorCode, String errorMessage) {
        return new FileUploadResult(false, null, errorCode, errorMessage);
    }
}
