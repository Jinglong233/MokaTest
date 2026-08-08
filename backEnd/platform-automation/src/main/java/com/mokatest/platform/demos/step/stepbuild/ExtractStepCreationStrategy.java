package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.ExtractStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.ExtractStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description 关联提取步骤创建策略
 * @Date 2025/11/18 19:29
 **/
@Component
public class ExtractStepCreationStrategy extends StepAbstractBuilder {

    public ExtractStepCreationStrategy() {
        super(StepType.EXTRACT);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.EXTRACT.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        ExtractStepDTO extractStepDTO = gson.fromJson(stepDetail.toString(), ExtractStepDTO.class);

        ExtractStep extractStep = new ExtractStep(extractStepDTO);
        setCommonProperties(extractStep, stepEntity);

        return extractStep;
    }
}
