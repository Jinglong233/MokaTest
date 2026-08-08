package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.ConditionalRelationship;
import com.mokatest.platform.demos.condation.ConditionBuilder;
import com.mokatest.platform.demos.condation.TestCondition;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.AssertStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.AssertStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author JingLong
 * @Description 断言创建类
 * @Date 2025/8/17 10:39
 **/
@Component
public class AssertStepCreationStrategy extends StepAbstractBuilder {


    @Resource
    private ConditionBuilder conditionBuilder;

    public AssertStepCreationStrategy() {
        super(StepType.ASSERT);
    }

    @Override
    public boolean supports(String stepType) {
        return "ASSERT".equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        AssertStepDTO assertStepDTO = gson.fromJson(stepDetail.toString(), AssertStepDTO.class);
        List<AssertStepDTO> condition = new ArrayList<>();
        condition.add(assertStepDTO);
        List<TestCondition> testConditions = conditionBuilder.loadCondition(condition);

        AssertStep assertStep = new AssertStep(testConditions, ConditionalRelationship.AND);
        setCommonProperties(assertStep, stepEntity);

        return assertStep;
    }
}
