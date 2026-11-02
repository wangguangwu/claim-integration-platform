package com.wangguangwu.claim.application.infrastructure.claimcase.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 案件关联 MyBatis-Plus Mapper，查询条件统一由仓储使用 Wrapper 构造。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Mapper
public interface ClaimCaseRelationDOMapper extends BaseMapper<ClaimCaseRelationDO> {
}
