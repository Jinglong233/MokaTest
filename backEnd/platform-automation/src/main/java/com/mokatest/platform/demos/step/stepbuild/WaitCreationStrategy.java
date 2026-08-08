package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.uiEnum.WaitType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.WaitStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.WaitStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description 等待步骤创建
 * @Date 2025/8/17 11:25
 **/
@Component
public class WaitCreationStrategy extends StepAbstractBuilder {

    public WaitCreationStrategy() {
        super(StepType.WAIT);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.WAIT.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        WaitStepDTO waitStepDTO = gson.fromJson(stepDetail.toString(), WaitStepDTO.class);

        WaitStep waitStep = new WaitStep(WaitType.valueOf(waitStepDTO.getWaitType().toString()),
                waitStepDTO.getWaitTime());
        setCommonProperties(waitStep, stepEntity);

        return waitStep;
    }
}
