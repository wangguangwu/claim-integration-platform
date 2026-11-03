package com.wangguangwu.claim.application.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * InteractionLog 表 MyBatis Mapper。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Mapper
public interface InteractionLogDOMapper extends BaseMapper<InteractionLogDO> {
}
