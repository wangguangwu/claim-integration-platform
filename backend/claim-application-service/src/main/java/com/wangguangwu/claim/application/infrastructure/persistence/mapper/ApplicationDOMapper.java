package com.wangguangwu.claim.application.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangguangwu.claim.application.infrastructure.persistence.dataobject.ApplicationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Application 主表 MyBatis-Plus Mapper，查询与更新条件统一由仓储使用 Wrapper 构造。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Mapper
public interface ApplicationDOMapper extends BaseMapper<ApplicationDO> {
}
