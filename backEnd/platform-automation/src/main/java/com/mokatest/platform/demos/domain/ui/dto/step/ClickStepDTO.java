package com.mokatest.platform.demos.domain.ui.dto.step;// 点击操作

import com.mokatest.platform.demos.domain.ui.uiEnum.click.ClickType;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import lombok.Data;

@Data
public class ClickStepDTO extends StepBaseDTO {
    private String stepType; // 步骤类型
    private ClickType clickType; // 点击类型
    private ElementDTO element; // 元素
    private String optionValue; // 下拉选项值
}