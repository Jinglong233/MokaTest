package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.HoverStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.HoverStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description 悬停步骤创建类
 * @Date 2025/10/29 19:41
 **/
@Component
public class HoverStepCreationStrategy extends StepAbstractBuilder {

    public HoverStepCreationStrategy() {
        super(StepType.HOVER);
    }

    @Override
    public boolean supports(String stepType) {
        return "HOVER".equalsIgnoreCase(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {

        // 获取步骤详情
        Object stepDetail = stepEntity.getStepDetail();

        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        HoverStepDTO hoverStepDTO = gson.fromJson(stepDetail.toString(), HoverStepDTO.class);


        HoverStep hoverStep = new HoverStep(hoverStepDTO.getElement());
        setCommonProperties(hoverStep, stepEntity);

        return hoverStep;
    }
}
