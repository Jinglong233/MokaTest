package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;

// 刷新步骤
public class RefreshStep extends AbstractTestStep {


    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        context.getCurrentPage().reload();
         context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);

        return result;
    }
}