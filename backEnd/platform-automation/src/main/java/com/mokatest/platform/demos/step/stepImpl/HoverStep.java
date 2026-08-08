package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractNecessaryElementTestStep;
import com.microsoft.playwright.Locator;

/**
 * @Author JingLong
 * @Description 鼠标悬停操作
 * @Date 2025/10/29 19:39
 **/
public class HoverStep extends AbstractNecessaryElementTestStep {
    public HoverStep(ElementDTO element) {
        super(element);
    }

    @Override
    protected StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();
        Locator locator = getLocator(context);
        locator.hover();
         context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        return result;
    }
}
