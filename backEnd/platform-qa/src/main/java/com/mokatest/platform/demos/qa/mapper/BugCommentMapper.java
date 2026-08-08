package com.mokatest.platform.demos.qa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.qa.domain.BugComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BugCommentMapper extends BaseMapper<BugComment> {
}
