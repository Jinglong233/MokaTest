package com.mokatest.platform.demos.domain.ui.dto.step;// 点击操作

import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import lombok.Data;

@Data
public class HoverStepDTO extends StepBaseDTO {
    private String stepType; // 步骤类型
    private ElementDTO element; // 元素
}