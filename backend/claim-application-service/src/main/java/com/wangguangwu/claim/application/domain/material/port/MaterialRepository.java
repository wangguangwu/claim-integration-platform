package com.wangguangwu.claim.application.domain.material.port;

import com.wangguangwu.claim.application.domain.material.Material;
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
     * 查询申请当前有效材料，按创建顺序返回。
     *
     * @param applicationNo 平台申请编号
     * @return 未删除材料
     */
    List<Material> findByApplicationNoAndDeletedFalse(String applicationNo);

    /**
     * 将某申请当前有效材料逻辑删除。
     *
     * @param applicationNo 平台申请编号
     */
    void softDeleteByApplicationNo(String applicationNo);

    /**
     * 以待处理状态为更新条件写入处理结果，重复消息不覆盖已完成结果。
     *
     * @param material 已写入处理结果的材料
     * @return 更新成功时为 true
     */
    boolean updateProcessResult(Material material);
}
