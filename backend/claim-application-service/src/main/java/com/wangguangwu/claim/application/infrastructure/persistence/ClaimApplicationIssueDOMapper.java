package com.wangguangwu.claim.application.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问题件 MyBatis-Plus Mapper，查询与更新条件统一由仓储使用 Wrapper 构造。
 *
 * @author wangguangwu
 * @since 2026-10-31
 */
@Mapper
public interface ClaimApplicationIssueDOMapper extends BaseMapper<ClaimApplicationIssueDO> {
}
