package com.mokatest.platform.demos.domain.ui.dto.step;// 键盘操作


import com.mokatest.platform.demos.domain.ui.uiEnum.keyboard.KeyboardInputType;
import com.mokatest.platform.demos.domain.ui.uiEnum.keyboard.KeyboardKey;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import lombok.Data;

@Data
public class KeyboardStepDTO extends StepBaseDTO {
    private String stepType; // 步骤类型
    private ElementDTO element; // 操作元素
    private KeyboardInputType inputType; // 输入类型

    private KeyboardKey keyboardKey; // 按键
    private String inputValue; // 输入内容
    private Integer isAdditional; // 是否追加输入
}