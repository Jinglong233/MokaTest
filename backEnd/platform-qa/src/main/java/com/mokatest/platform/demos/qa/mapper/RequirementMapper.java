package com.mokatest.platform.demos.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.qa.domain.Requirement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 需求池 Mapper
 */
@Mapper
public interface RequirementMapper extends BaseMapper<Requirement> {
}
