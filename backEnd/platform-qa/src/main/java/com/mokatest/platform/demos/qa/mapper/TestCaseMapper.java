package com.mokatest.platform.demos.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.qa.domain.TestCase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文字用例 Mapper
 */
@Mapper
public interface TestCaseMapper extends BaseMapper<TestCase> {
}
