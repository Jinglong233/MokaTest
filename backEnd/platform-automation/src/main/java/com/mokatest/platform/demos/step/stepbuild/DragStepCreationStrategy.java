package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.DragElementStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.DragStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description 拖拽步骤构建类
 * @Date 2025/12/24 19:21
 **/
@Component
public class DragStepCreationStrategy extends StepAbstractBuilder {

    public DragStepCreationStrategy() {
        super(StepType.DRAG);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.DRAG.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        // 获取步骤详情
        Object stepDetail = stepEntity.getStepDetail();

        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        DragElementStepDTO dragElementStepDTO = gson.fromJson(stepDetail.toString(), DragElementStepDTO.class);


        DragStep dragStep = new DragStep(dragElementStepDTO.getDragElement(), dragElementStepDTO.getTargetElement());
        setCommonProperties(dragStep, stepEntity);

        return dragStep;
    }
}
