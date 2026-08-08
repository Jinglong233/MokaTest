package com.mokatest.platform.demos.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.qa.domain.BugOperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BugOperationLogMapper extends BaseMapper<BugOperationLog> {
}
