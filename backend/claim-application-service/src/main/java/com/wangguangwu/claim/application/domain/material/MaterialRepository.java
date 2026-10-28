package com.wangguangwu.claim.application.domain.material;

import java.util.List;

/**
 * Material 聚合的持久化端口。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public interface MaterialRepository {

    /**
     * 批量保存同一材料批次。
     *
     * @param materials 待保存材料
     */
    void insertAll(List<Material> materials);

    /**
     * 将某申请当前有效材料逻辑删除。
     *
     * @param applicationNo 平台申请编号
     */
    void softDeleteByApplicationNo(String applicationNo);
}
