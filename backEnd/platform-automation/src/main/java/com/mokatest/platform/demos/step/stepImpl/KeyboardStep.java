package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.keyboard.KeyboardInputType;
import com.mokatest.platform.demos.domain.ui.uiEnum.keyboard.KeyboardKey;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractNecessaryElementTestStep;
import com.mokatest.platform.demos.util.VariableReplacer;
import com.microsoft.playwright.Locator;

/**
 * 键盘步骤
 */
public class KeyboardStep extends AbstractNecessaryElementTestStep {

    private KeyboardInputType inputType; // 输入类型

    private KeyboardKey keyboardKey; // 按键
    private String inputValue; // 输入内容
    private Integer isAdditional; // 是否追加输入


    public KeyboardStep(ElementDTO element, KeyboardInputType inputType, KeyboardKey keyboardKey, String inputValue,
                        Integer isAdditional) {
        super(element);
        this.inputType = inputType;
        this.keyboardKey = keyboardKey;
        this.inputValue = inputValue;
        this.isAdditional = isAdditional;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();
        switch (inputType) {
            case NORMAL -> {
                Locator locator = getLocator(context);
                // 统一替换：先 ${var}/{{var}} 变量，再 @mock/{{__函数__}} 函数（VariableReplacer 内部串联，二者可混用）
                String finalValue = VariableReplacer.replace(inputValue, context.getVariables());

                if (isAdditional == 1) { // 追加输入
                    // 先获取当前输入框内容
                    String s = locator.inputValue();
                    locator.fill(s + finalValue);
                } else {
                    locator.fill(finalValue);
                }
            }
            case KEYBOARD -> {
                switch (keyboardKey) {
                    case ENTER -> context.getCurrentPage().keyboard().press("Enter");
                    // Delete删除
                    case DELETE -> context.getCurrentPage().keyboard().press("Delete");
                    // 空格
                    case SPACE -> context.getCurrentPage().keyboard().press("Space");
                    // 回退Backspace
                    case BACKSPACE -> context.getCurrentPage().keyboard().press("Backspace");
                    // 键盘输入
                    case PRESS_INPUT -> context.getCurrentPage().keyboard().insertText(inputValue);
                }
            }
        }
        context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);

        return result;
    }
}
