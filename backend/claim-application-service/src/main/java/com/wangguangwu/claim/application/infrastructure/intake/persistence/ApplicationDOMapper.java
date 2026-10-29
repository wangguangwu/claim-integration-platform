package com.wangguangwu.claim.application.infrastructure.intake.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 理赔申请主表 MyBatis-Plus Mapper，查询与更新条件统一由仓储使用 Wrapper 构造。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Mapper
public interface ApplicationDOMapper extends BaseMapper<ApplicationDO> {
}
