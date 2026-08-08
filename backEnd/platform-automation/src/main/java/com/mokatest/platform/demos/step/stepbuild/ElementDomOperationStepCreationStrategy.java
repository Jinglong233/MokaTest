package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.ElementDomOperationStepDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.ElementDomOperationStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description 元素DOM操作步骤创建类
 * @Date 2026/7/16
 **/
@Component
public class ElementDomOperationStepCreationStrategy extends StepAbstractBuilder {

    public ElementDomOperationStepCreationStrategy() {
        super(StepType.ELEMENT_DOM_OPERATION);
    }

    @Override
    public boolean supports(String stepType) {
        return "ELEMENT_DOM_OPERATION".equalsIgnoreCase(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {

        // 获取步骤详情
        Object stepDetail = stepEntity.getStepDetail();

        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        ElementDomOperationStepDTO domOperationStepDTO = gson.fromJson(stepDetail.toString(), ElementDomOperationStepDTO.class);

        ElementDomOperationStep domOperationStep = new ElementDomOperationStep(domOperationStepDTO);
        setCommonProperties(domOperationStep, stepEntity);

        return domOperationStep;
    }
}
