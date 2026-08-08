package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.uiEnum.browser.SwitchTabMode;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.SwitchTabStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.SwitchTabStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/30 17:38
 **/
@Component
public class SwitchStepCreationStrategy extends StepAbstractBuilder {

    public SwitchStepCreationStrategy() {
        super(StepType.SWITCH_TAB);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.SWITCH_TAB.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        SwitchTabStepDTO switchTabStepDTO = gson.fromJson(stepDetail.toString(), SwitchTabStepDTO.class);

        SwitchTabStep switchTabStep =
                new SwitchTabStep(SwitchTabMode.valueOf(switchTabStepDTO.getSwitchTabMode().toString()),
                        switchTabStepDTO.getCustomIndex());
        setCommonProperties(switchTabStep, stepEntity);

        return switchTabStep;
    }
}