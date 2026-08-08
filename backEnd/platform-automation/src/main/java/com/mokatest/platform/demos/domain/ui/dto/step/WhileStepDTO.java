package com.mokatest.platform.demos.domain.ui.dto.step;// while循环操作


import com.mokatest.platform.demos.domain.ui.uiEnum.condation.ConditionalRelationship;
import lombok.Data;

import java.util.List;

@Data
public class WhileStepDTO extends StepBaseDTO {
    private String stepType;

    // 条件关系
    private ConditionalRelationship conditionalRelationship;

    // 条件列表
    private List<AssertStepDTO> conditionList;

    // 最大循环次数
    private Integer maxLoopCount;
}






