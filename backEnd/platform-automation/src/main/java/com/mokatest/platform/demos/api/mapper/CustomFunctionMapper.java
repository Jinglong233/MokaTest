package com.mokatest.platform.demos.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.api.domain.CustomFunction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 自定义公共函数 Mapper
 *
 * @author JingLong
 * @since 2026-07-31
 */
@Mapper
public interface CustomFunctionMapper extends BaseMapper<CustomFunction> {

    /**
     * 按 id 查询（含已逻辑删除，供函数调用链路给出明确错误提示）
     */
    @Select("SELECT * FROM custom_function WHERE id = #{id}")
    CustomFunction selectByIdIncludeDeleted(@Param("id") Integer id);
}
