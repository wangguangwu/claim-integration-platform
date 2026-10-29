package com.wangguangwu.claim.application.infrastructure.integration.persistence.consumption;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ConsumedEvent 表 MyBatis Mapper。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Mapper
public interface ConsumedEventDOMapper extends BaseMapper<ConsumedEventDO> {
}
