package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.WaitType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;

/**
 * @Author JingLong
 * @Description 等待步骤
 * @Date 2025/8/22 15:41
 **/
public class WaitStep extends AbstractTestStep {

    // 等待类型
    private WaitType waitType;
    // 等待时间
    private Integer waitTime;

    public WaitStep(WaitType waitType, Integer waitTime) {
        this.waitType = waitType;
        this.waitTime = waitTime;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        switch (waitType) {
            case TIME -> context.getCurrentPage().waitForTimeout(waitTime * 1000);
        }
         context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);

        return result;
    }
}
