package com.wangguangwu.claim.application.infrastructure.material.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 文件系统 Feign 接口，本地指向本服务的模拟实现，生产替换地址即可。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@FeignClient(name = "claim-file-system", url = "${claim.integration.file-system.url}")
public interface FileSystemFeignClient {

    /**
     * 上传单份材料。
     *
     * @param request 上传请求
     * @return 上传结果
     */
    @PostMapping("/mock/file-system/v1/uploads")
    FileUploadResponse upload(@RequestBody FileUploadRequest request);

    /**
     * 文件上传请求。
     *
     * @param applicationNo 平台申请编号
     * @param batchNo       材料批次编号
     * @param materialType  材料类型
     * @param sourceUrl     租户提交的初始文件地址
     */
    record FileUploadRequest(String applicationNo, String batchNo, String materialType, String sourceUrl) {
    }

    /**
     * 文件上传结果。
     *
     * @param success         是否成功
     * @param fileId          文件标识
     * @param uploadedFileUrl 上传后文件地址
     * @param errorCode       失败错误码
     * @param errorMessage    失败说明
     */
    record FileUploadResponse(boolean success, String fileId, String uploadedFileUrl, String errorCode,
                              String errorMessage) {
    }
}
