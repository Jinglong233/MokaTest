package com.mokatest.platform.demos.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.ai.domain.AiAdoptionDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiAdoptionDetailMapper extends BaseMapper<AiAdoptionDetail> {

    /**
     * 查询有效的已采纳 draftId（交叉验证 test_case 表，排除已删除的用例）
     * BUG#10 修复：删除用例后采纳计数不再虚高
     */
    @Select("SELECT ad.draft_id FROM ai_adoption_detail ad " +
            "LEFT JOIN test_case tc ON ad.case_id = tc.id " +
            "WHERE ad.record_no = #{recordNo} " +
            "AND (ad.case_id IS NULL OR tc.is_deleted = 0)")
    List<String> selectValidDraftIds(@Param("recordNo") String recordNo);
}
