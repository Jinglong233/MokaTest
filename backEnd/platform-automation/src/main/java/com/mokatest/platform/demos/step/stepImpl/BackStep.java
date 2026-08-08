package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;

/**
 * @Author JingLong
 * @Description 后退步骤
 * @Date 2025/8/30 17:10
 **/
public class BackStep extends AbstractTestStep {


    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();
        context.getCurrentPage().goBack();
         context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        return result;
    }
}
