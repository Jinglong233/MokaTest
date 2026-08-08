package com.mokatest.platform.demos.qa.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.qa.config.domain.ProjectConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目级统一配置 Mapper
 */
@Mapper
public interface ProjectConfigMapper extends BaseMapper<ProjectConfig> {
}
