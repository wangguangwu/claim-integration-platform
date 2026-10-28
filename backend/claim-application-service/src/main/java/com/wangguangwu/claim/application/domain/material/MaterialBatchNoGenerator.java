package com.wangguangwu.claim.application.domain.material;

/**
 * 材料批次编号生成端口。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface MaterialBatchNoGenerator {

    /**
     * 生成材料批次编号。
     *
     * @return 批次编号
     */
    String nextBatchNo();
}
