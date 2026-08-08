package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.uiEnum.click.ClickType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.ClickStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.ClickStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

@Component
public class ClickStepCreationStrategy extends StepAbstractBuilder {


    public ClickStepCreationStrategy() {
        super(StepType.CLICK);
    }

    @Override
    public boolean supports(String stepType) {
        return "CLICK".equalsIgnoreCase(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {

        // 获取步骤详情
        Object stepDetail = stepEntity.getStepDetail();

        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        ClickStepDTO clickStepDTO = gson.fromJson(stepDetail.toString(), ClickStepDTO.class);

        ClickStep clickStep = null;

        // 判断点击类型
        if (clickStepDTO.getClickType().equals(ClickType.SELECT)) {
            clickStep = new ClickStep(clickStepDTO.getElement(), clickStepDTO.getClickType(),clickStepDTO.getOptionValue());
        } else {
            clickStep = new ClickStep(clickStepDTO.getElement(), clickStepDTO.getClickType());
        }
        setCommonProperties(clickStep, stepEntity);

        return clickStep;
    }


}