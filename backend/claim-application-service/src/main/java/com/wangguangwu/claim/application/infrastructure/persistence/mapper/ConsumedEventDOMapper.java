package com.wangguangwu.claim.application.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangguangwu.claim.application.infrastructure.persistence.dataobject.ConsumedEventDO;
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
