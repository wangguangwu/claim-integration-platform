package com.wangguangwu.claim.application.infrastructure.persistence.convert;

import com.wangguangwu.claim.application.domain.material.Material;
import com.wangguangwu.claim.application.infrastructure.persistence.dataobject.MaterialDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Material 领域对象与数据对象映射器。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Mapper(componentModel = "spring")
public interface MaterialDataMapper {

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
     * 转换为领域对象。
     *
     * @param materialDO 数据对象
     * @return Material 领域对象
     */
    Material toDomain(MaterialDO materialDO);
}
