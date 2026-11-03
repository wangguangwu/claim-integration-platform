package com.wangguangwu.claim.application.infrastructure.persistence;

import com.wangguangwu.claim.application.domain.claim.Material;
import com.wangguangwu.claim.application.domain.claim.MaterialStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 聚合内材料实体与数据对象映射器。
 *
 * <p>材料不再持有平台申请编号，写方向由调用方传入根的申请编号；读方向显式调用材料自己的重建工厂，
 * 避免用 setter 逐字段绕过领域状态约束。</p>
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Mapper(componentModel = "spring")
public interface MaterialDataConverter {

    /**
     * 转换为持久化数据对象，归属申请编号由聚合根另行传入。
     *
     * @param material 材料实体
     * @return 数据对象
     */
    @Mapping(target = "claimApplicationNo", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MaterialDO toDataObject(Material material);

    /**
     * 转换为持久化数据对象并写入材料所属申请编号。
     *
     * @param claimApplicationNo 材料所属平台申请编号
     * @param material           材料实体
     * @return 数据对象
     */
    default MaterialDO toDataObject(String claimApplicationNo, Material material) {
        MaterialDO materialDO = toDataObject(material);
        if (materialDO != null) {
            materialDO.setClaimApplicationNo(claimApplicationNo);
        }
        return materialDO;
    }

    /**
     * 从数据对象重建材料实体。
     *
     * @param materialDO 数据对象
     * @return 材料实体
     */
    default Material toDomain(MaterialDO materialDO) {
        if (materialDO == null) {
            return null;
        }
        return Material.restore(materialDO.getId(), materialDO.getBatchNo(), materialDO.getMaterialType(),
                materialDO.getOriginalFileUrl(), materialDO.getUploadedFileUrl(),
                MaterialStatus.valueOf(materialDO.getStatus()), Boolean.TRUE.equals(materialDO.getDeleted()),
                materialDO.getDeletedAt());
    }
}
