package com.wangguangwu.claim.application.domain.claim;

import com.wangguangwu.claim.foundation.common.util.AssertUtil;

/**
 * 单份材料的异步处理结果，由文件处理器在事务外产出。
 *
 * <p>成功结果必须携带上传后的文件地址；失败结果忽略该字段。</p>
 *
 * @param materialId      材料主键
 * @param succeeded       是否处理成功
 * @param uploadedFileUrl 处理成功后的文件地址
 * @author wangguangwu
 * @since 2026-11-03
 */
public record MaterialProcessingResult(Long materialId, boolean succeeded, String uploadedFileUrl) {

    /**
     * 校验结果自洽，避免成功结果缺少可用文件地址。
     *
     * @param materialId      材料主键
     * @param succeeded       是否处理成功
     * @param uploadedFileUrl 处理成功后的文件地址
     */
    public MaterialProcessingResult {
        AssertUtil.notNull(materialId, "材料处理结果必须携带材料主键");
        if (succeeded) {
            AssertUtil.notBlank(uploadedFileUrl, "材料处理成功结果必须携带上传地址，materialId=" + materialId);
        }
    }
}
