package com.wangguangwu.claim.application.infrastructure.integration.persistence.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * OutboxEvent 表 MyBatis Mapper。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
@Mapper
public interface OutboxEventDOMapper extends BaseMapper<OutboxEventDO> {
}
