package com.mokatest.platform.demos.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.qa.domain.TestCaseAutoBind;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用例与自动化绑定关系 Mapper
 */
@Mapper
public interface TestCaseAutoBindMapper extends BaseMapper<TestCaseAutoBind> {
}
