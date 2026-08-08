package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.ClosePageStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.ClosePageStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/17 11:25
 **/
@Component
public class ClosePageCreationStrategy extends StepAbstractBuilder {

    public ClosePageCreationStrategy() {
        super(StepType.CLOSE_PAGE);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.CLOSE_PAGE.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        ClosePageStepDTO closePageStepDTO = gson.fromJson(stepDetail.toString(), ClosePageStepDTO.class);
        ClosePageStep closePageStep = new ClosePageStep(closePageStepDTO.getClosePageMode(),
                closePageStepDTO.getCustomIndex());

        setCommonProperties(closePageStep, stepEntity);

        return closePageStep;
    }
}
