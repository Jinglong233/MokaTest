package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.BackStep;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/30 17:10
 **/
@Component
public class BackCreationStrategy extends StepAbstractBuilder {

    public BackCreationStrategy() {
        super(StepType.BACK);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.BACK.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        BackStep backStep = new BackStep();
        setCommonProperties(backStep, stepEntity);
        return backStep;
    }
}