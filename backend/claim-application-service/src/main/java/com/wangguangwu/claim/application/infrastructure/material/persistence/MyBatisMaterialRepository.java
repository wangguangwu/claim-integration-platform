package com.wangguangwu.claim.application.infrastructure.material.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wangguangwu.claim.application.domain.material.Material;
import com.wangguangwu.claim.application.domain.material.MaterialRepository;
import com.wangguangwu.claim.application.domain.material.MaterialStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的 Material 聚合仓储实现。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@RequiredArgsConstructor
@Repository
public class MyBatisMaterialRepository implements MaterialRepository {

    private static final boolean DELETED = true;
    private static final boolean NOT_DELETED = false;

    private final MaterialDOMapper materialMapper;
    private final MaterialDataConverter materialDataConverter;

    /**
     * 逐条保存同批次材料，避免 MyBatis 批量执行器跨事务缓存造成行为不透明。
     *
     * @param materials 待保存材料
     */
    @Override
    public void insertAll(List<Material> materials) {
        for (Material material : materials) {
            materialMapper.insert(materialDataConverter.toDataObject(material));
        }
    }

    /**
     * 逻辑删除申请当前有效材料，并记录删除时间。
     *
     * @param applicationNo 平台申请编号
     */
    @Override
    public void softDeleteByApplicationNo(String applicationNo) {
        materialMapper.update(null, Wrappers.lambdaUpdate(MaterialDO.class)
                .eq(MaterialDO::getApplicationNo, applicationNo)
                .eq(MaterialDO::getDeleted, NOT_DELETED)
                .set(MaterialDO::getDeleted, DELETED)
                .set(MaterialDO::getDeletedAt, LocalDateTime.now()));
    }

    /**
     * 查询申请当前有效材料，按创建顺序返回。
     *
     * @param applicationNo 平台申请编号
     * @return 未删除材料
     */
    @Override
    public List<Material> findByApplicationNoAndDeletedFalse(String applicationNo) {
        return materialMapper.selectList(Wrappers.lambdaQuery(MaterialDO.class)
                        .eq(MaterialDO::getApplicationNo, applicationNo)
                        .eq(MaterialDO::getDeleted, NOT_DELETED)
                        .orderByAsc(MaterialDO::getId))
                .stream()
                .map(materialDataConverter::toDomain)
                .toList();
    }

    /**
     * 以待处理状态为更新条件写入处理结果，重复消息不覆盖已完成结果。
     *
     * @param material 已写入处理结果的材料
     * @return 更新成功时为 true
     */
    @Override
    public boolean updateProcessResult(Material material) {
        MaterialDO materialDO = materialDataConverter.toDataObject(material);
        return materialMapper.update(null, Wrappers.lambdaUpdate(MaterialDO.class)
                .eq(MaterialDO::getId, materialDO.getId())
                .eq(MaterialDO::getStatus, MaterialStatus.PENDING_PROCESS.name())
                .set(MaterialDO::getStatus, materialDO.getStatus())
                .set(MaterialDO::getUploadedFileUrl, materialDO.getUploadedFileUrl())) == 1;
    }
}
