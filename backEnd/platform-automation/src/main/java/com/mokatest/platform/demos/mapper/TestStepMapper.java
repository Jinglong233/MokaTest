package com.mokatest.platform.demos.mapper;

import com.mokatest.platform.demos.domain.ui.TestStep;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author: JingLong
* @description 针对表【test_step(测试步骤主表)】的数据库操作Mapper
* @createDate 2025-07-26 11:10:33
* @Entity com.mokaTest.platform.demos.domain.ui.TestStep
*/
@Mapper
public interface TestStepMapper extends BaseMapper<TestStep> {
    /**
     * 根据场景ID列表查询所有测试步骤ID
     */
    @Select("<script>" +
            "SELECT id FROM test_step WHERE scenario_id IN " +
            "<foreach collection='sceneIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<Integer> findBySceneIds(@Param("sceneIds") List<Integer> sceneIds);
}




