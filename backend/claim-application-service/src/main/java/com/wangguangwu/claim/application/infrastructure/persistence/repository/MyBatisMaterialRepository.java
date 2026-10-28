package com.wangguangwu.claim.application.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wangguangwu.claim.application.domain.material.Material;
import com.wangguangwu.claim.application.domain.material.MaterialRepository;
import com.wangguangwu.claim.application.infrastructure.persistence.convert.MaterialDataMapper;
import com.wangguangwu.claim.application.infrastructure.persistence.dataobject.MaterialDO;
import com.wangguangwu.claim.application.infrastructure.persistence.mapper.MaterialDOMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的 Material 聚合仓储实现。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Repository
public class MyBatisMaterialRepository implements MaterialRepository {

    private static final boolean DELETED = true;
    private static final boolean NOT_DELETED = false;

    private final MaterialDOMapper materialMapper;
    private final MaterialDataMapper materialDataMapper;

    public MyBatisMaterialRepository(MaterialDOMapper materialMapper, MaterialDataMapper materialDataMapper) {
        this.materialMapper = materialMapper;
        this.materialDataMapper = materialDataMapper;
    }

    /**
     * 逐条保存同批次材料，避免 MyBatis 批量执行器跨事务缓存造成行为不透明。
     *
     * @param materials 待保存材料
     */
    @Override
    public void insertAll(List<Material> materials) {
        for (Material material : materials) {
            materialMapper.insert(materialDataMapper.toDataObject(material));
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
}
