package com.wangguangwu.claim.application.infrastructure.persistence.convert;

import com.wangguangwu.claim.application.domain.application.Application;
import com.wangguangwu.claim.application.infrastructure.persistence.dataobject.ApplicationDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Application 领域对象与数据对象映射器。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Mapper(componentModel = "spring")
public interface ApplicationDataMapper {

    /**
     * 转换为持久化数据对象。
     *
     * @param application Application 聚合根
     * @return 数据对象
     */
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ApplicationDO toDataObject(Application application);

    /**
     * 转换为领域聚合根。
     *
     * @param applicationDO 数据对象
     * @return Application 聚合根
     */
    Application toDomain(ApplicationDO applicationDO);
}
