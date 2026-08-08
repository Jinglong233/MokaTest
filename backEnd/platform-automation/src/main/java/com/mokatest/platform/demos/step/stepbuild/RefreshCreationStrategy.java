package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.RefreshStep;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/17 11:25
 **/
@Component
public class RefreshCreationStrategy extends StepAbstractBuilder {

    public RefreshCreationStrategy() {
        super(StepType.REFRESH);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.REFRESH.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        RefreshStep refreshStep = new RefreshStep();
        setCommonProperties(refreshStep, stepEntity);
        return refreshStep;
    }
}
