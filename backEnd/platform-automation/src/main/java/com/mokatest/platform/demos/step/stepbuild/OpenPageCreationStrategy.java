package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.OpenPageStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.OpenPageStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/17 11:25
 **/
@Component
public class OpenPageCreationStrategy extends StepAbstractBuilder {

    public OpenPageCreationStrategy() {
        super(StepType.OPEN_PAGE);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.OPEN_PAGE.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        OpenPageStepDTO openPageStepDTO = gson.fromJson(stepDetail.toString(), OpenPageStepDTO.class);
        OpenPageStep openPageStep = new OpenPageStep(openPageStepDTO.getUrl(), openPageStepDTO.getRecover());
        setCommonProperties(openPageStep, stepEntity);
        return openPageStep;
    }
}
