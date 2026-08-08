package com.mokatest.platform.demos.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.qa.domain.Bug;
import org.apache.ibatis.annotations.Mapper;

/**
 * BUG池 Mapper
 */
@Mapper
public interface BugMapper extends BaseMapper<Bug> {
}
