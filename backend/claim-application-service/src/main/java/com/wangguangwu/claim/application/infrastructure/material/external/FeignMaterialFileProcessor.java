package com.wangguangwu.claim.application.infrastructure.material.external;

import com.wangguangwu.claim.application.domain.material.FileUploadResult;
import com.wangguangwu.claim.application.domain.material.port.MaterialFileProcessor;
import com.wangguangwu.claim.application.domain.material.MaterialUploadRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 材料文件上传的 Feign 适配器，只做端口与外部报文转换。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Component
public class FeignMaterialFileProcessor implements MaterialFileProcessor {

    private final FileSystemFeignClient fileSystemFeignClient;

    /**
     * 上传材料并转换文件系统返回结果，异常向上抛出表示文件系统不可用。
     *
     * @param request 上传请求
     * @return 上传结果
     */
    @Override
    public FileUploadResult upload(MaterialUploadRequest request) {
        FileSystemFeignClient.FileUploadResponse response = fileSystemFeignClient.upload(
                new FileSystemFeignClient.FileUploadRequest(request.applicationNo(), request.batchNo(),
                        request.materialType(), request.sourceUrl()));
        if (response.success()) {
            return FileUploadResult.succeeded(response.uploadedFileUrl());
        }
        return FileUploadResult.failed(response.errorCode(), response.errorMessage());
    }
}
