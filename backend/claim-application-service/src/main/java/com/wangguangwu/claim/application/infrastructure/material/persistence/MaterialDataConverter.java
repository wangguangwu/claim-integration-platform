package com.wangguangwu.claim.application.infrastructure.material.persistence;

import com.wangguangwu.claim.application.domain.material.Material;
import com.wangguangwu.claim.application.domain.material.MaterialStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Material 领域对象与数据对象映射器。
 *
 * <p>写方向由 MapStruct 依据 getter 生成；读方向显式调用材料自己的反水化工厂，
 * 避免用 setter 逐字段绕过领域状态约束。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Mapper(componentModel = "spring")
public interface MaterialDataConverter {

    /**
     * 转换为持久化数据对象。
     *
     * @param material Material 领域对象
     * @return 数据对象
     */
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MaterialDO toDataObject(Material material);

    /**
     * 从数据对象重建材料聚合。
     *
     * @param materialDO 数据对象
     * @return 材料聚合
     */
    default Material toDomain(MaterialDO materialDO) {
        if (materialDO == null) {
            return null;
        }
        return Material.restore(materialDO.getId(), materialDO.getClaimApplicationNo(), materialDO.getBatchNo(),
                materialDO.getMaterialType(), materialDO.getOriginalFileUrl(), materialDO.getUploadedFileUrl(),
                MaterialStatus.valueOf(materialDO.getStatus()), Boolean.TRUE.equals(materialDO.getDeleted()));
    }
}
