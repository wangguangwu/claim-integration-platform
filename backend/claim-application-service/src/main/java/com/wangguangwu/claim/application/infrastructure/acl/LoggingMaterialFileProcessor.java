package com.wangguangwu.claim.application.infrastructure.acl;

import com.wangguangwu.claim.application.application.port.MaterialFileProcessor;
import com.wangguangwu.claim.application.application.port.model.FileUploadResult;
import com.wangguangwu.claim.application.application.port.model.MaterialUploadRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件系统的日志实现：未接入真实文件服务，按租户提交的原始地址登记材料。
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@Slf4j
@Component
public class LoggingMaterialFileProcessor implements MaterialFileProcessor {

    /**
     * 上传材料；未接入文件系统，只记录调用日志并保留原始文件地址。
     *
     * @param request 上传请求
     * @return 成功结果，文件地址为租户提交的原始地址
     */
    @Override
    public FileUploadResult upload(MaterialUploadRequest request) {
        log.info("文件系统未接入，按原始地址登记材料，claimApplicationNo={}，materialType={}",
                request.claimApplicationNo(), request.materialType());
        return FileUploadResult.succeeded(request.sourceUrl());
    }
}
