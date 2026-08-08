package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import lombok.Data;

/**
 * @Author JingLong
 * @Description 前进步骤
 * @Date 2025/8/30 17:07
 **/
@Data
public class ForwardStep extends AbstractTestStep {


    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        context.getCurrentPage().goForward();

        context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        return result;
    }
}