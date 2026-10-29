package com.wangguangwu.claim.application.domain.material.port;

import com.wangguangwu.claim.application.domain.material.FileUploadResult;
import com.wangguangwu.claim.application.domain.material.MaterialUploadRequest;

/**
 * 材料文件上传端口。
 *
 * <p>文件系统不属于本项目实现范围，基础设施层以外部接口适配，本地以模拟实现返回上传地址。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface MaterialFileProcessor {

    /**
     * 上传单份材料并返回上传后地址。
     *
     * @param request 上传请求
     * @return 上传结果；文件系统明确拒绝时返回失败结果
     */
    FileUploadResult upload(MaterialUploadRequest request);
}
