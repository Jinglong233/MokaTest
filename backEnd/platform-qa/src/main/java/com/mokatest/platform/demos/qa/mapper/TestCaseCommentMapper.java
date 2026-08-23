package com.mokatest.platform.demos.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.qa.domain.TestCaseComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TestCaseCommentMapper extends BaseMapper<TestCaseComment> {
}
