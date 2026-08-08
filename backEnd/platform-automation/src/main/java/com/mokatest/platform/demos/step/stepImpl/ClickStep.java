package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.click.ClickType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractNecessaryElementTestStep;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.MouseButton;

/**
 * 点击步骤
 */
public class ClickStep extends AbstractNecessaryElementTestStep {
    private ClickType clickType;

    private String optionValue;


    public ClickStep(ElementDTO element, ClickType clickType) {
        super(element);
        this.clickType = clickType;
    }

    public ClickStep(ElementDTO element, ClickType clickType, String optionValue) {
        super(element);
        this.clickType = clickType;
        this.optionValue = optionValue;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        Locator locator = getLocator(context);
        // 获取点击类型
        switch (clickType) {
            case DOUBLE_CLICK -> {
                locator.dblclick();
            }
            case RIGHT_CLICK -> {
                locator.click(new Locator.ClickOptions().setButton(MouseButton.RIGHT));
            }
            case SINGLE_CLICK -> {
                locator.click();

            }
            // 长按
            case LONG_PRESS -> {
                locator.click(new Locator.ClickOptions().setDelay(1000));
            }
            // 下拉选择
            case SELECT -> {
                locator.selectOption(optionValue);
            }
        }
        context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);

        return result;
    }


}