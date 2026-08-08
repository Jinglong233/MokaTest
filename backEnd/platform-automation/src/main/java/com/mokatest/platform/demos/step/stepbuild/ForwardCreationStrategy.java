package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.ForwardStep;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/30 17:08
 **/
@Component
public class ForwardCreationStrategy extends StepAbstractBuilder {

    public ForwardCreationStrategy() {
        super(StepType.FORWARD);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.FORWARD.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        ForwardStep forwardStep = new ForwardStep();
        setCommonProperties(forwardStep, stepEntity);
        return forwardStep;
    }
}
