package com.mokatest.platform.demos.domain.ui.dto.step;// 拖拽元素


import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import lombok.Data;

@Data
public class DragElementStepDTO extends StepBaseDTO {
    private String stepType; // 步骤类型
    private ElementDTO dragElement; // 被拖拽元素
    private ElementDTO targetElement; // 目标元素
}