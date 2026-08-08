package com.mokatest.platform.demos.domain.ui.dto;// 条件传输对象

import com.mokatest.platform.demos.domain.ui.uiEnum.condation.ConditionalRelationship;
import com.mokatest.platform.demos.domain.ui.dto.step.AssertStepDTO;
import lombok.Data;

import java.util.List;

@Data
public class ConditionDTO {
    private ConditionalRelationship conditionalRelationship;// 条件关系
    private List<AssertStepDTO> asserts; // 断言列表
}