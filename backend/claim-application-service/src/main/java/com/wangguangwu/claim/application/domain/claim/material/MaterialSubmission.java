package com.wangguangwu.claim.application.domain.claim.material;

import com.wangguangwu.claim.foundation.common.util.AssertUtil;

/**
 * 理赔申请随单提交的材料，只承载协议归一化后的材料类型与初始文件地址。
 *
 * <p>它是理赔申请提交内容的一部分，聚合根据此创建材料实体；本值对象不参与材料状态的拼装。</p>
 *
 * @param materialType    材料类型
 * @param originalFileUrl 租户提交的文件地址
 * @author wangguangwu
 * @since 2026-10-27
 */
public record MaterialSubmission(String materialType, String originalFileUrl) {

    /**
     * 创建提交材料，两项字段是材料不变式，非法输入在构造阶段拒绝。
     */
    public MaterialSubmission {
        AssertUtil.notBlank(materialType, "材料类型不能为空");
        AssertUtil.notBlank(originalFileUrl, "初始文件地址不能为空");
    }
}
