package com.mokatest.platform.demos.domain.ui.dto.step;// if判断操作

import com.mokatest.platform.demos.domain.ui.uiEnum.condation.ConditionalRelationship;
import lombok.Data;

import java.util.List;

@Data
public class IFStepDTO extends StepBaseDTO {
    private String stepType;

    private List<AssertStepDTO> conditionList;


    // 条件关系
    private ConditionalRelationship conditionalRelationship;
}






