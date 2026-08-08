package com.mokatest.platform.demos.domain.ui.dto.step;

import com.mokatest.platform.demos.domain.ui.Setting;
import lombok.Data;

import java.util.List;

@Data
public abstract class StepBaseDTO {
    // 步骤名称
    private String stepName;

//    // 步骤类型
//    private StepType stepType;

    // 断言列表
    private List<AssertStepDTO> assertList;

    // 提取列表
    private List<ExtractStepDTO> extractList;

    // 步骤设置
    private Setting setting;

}