package com.mokatest.platform.demos.step.stepbuild;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.ConditionalRelationship;
import com.mokatest.platform.demos.condation.ConditionBuilder;
import com.mokatest.platform.demos.condation.TestCondition;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.AssertStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.WhileStepDTO;
import com.mokatest.platform.demos.mapper.TestStepMapper;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.WhileCycleStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @Author JingLong
 * @Description whlie循环步骤创建器
 * @Date 2025/7/29 17:45
 **/

@Component
public class WhileCycleStepStrategy extends StepAbstractBuilder {

    @Resource
    private ConditionBuilder conditionBuilder;

    @Resource
    private TestStepMapper testStepMapper;

    private final StepBuilderFactory factory;

    // 使用@Lazy打破循环
    @Autowired
    public WhileCycleStepStrategy(@Lazy StepBuilderFactory stepBuilderFactory) {
        super(StepType.WHILE);
        this.factory = stepBuilderFactory;
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.WHILE.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {

        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        WhileStepDTO whileStepDTO = gson.fromJson(stepDetail.toString(), WhileStepDTO.class);

        List<AssertStepDTO> conditionList = whileStepDTO.getConditionList();
        List<TestCondition> testConditions = conditionBuilder.loadCondition(conditionList);
        ConditionalRelationship conditionalRelationship = whileStepDTO.getConditionalRelationship();
        Integer maxLoopCount = whileStepDTO.getMaxLoopCount();


        // 构建子步骤
        List<AbstractTestStep> childrenStep = new ArrayList<>();
        // 递归构造树状操作步骤
        QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", stepEntity.getId());
        queryWrapper.eq("scenario_id", stepEntity.getScenarioId());
        List<TestStep> testSteps = testStepMapper.selectList(queryWrapper);
        // 排序
        if (testSteps != null && testSteps.size() > 0) {
            testSteps.sort(Comparator.comparing(TestStep::getOrderIndex));
            for (TestStep testStep : testSteps) {
                AbstractTestStep stepImpl = factory.build(testStep.getStepType(), testStep);
                childrenStep.add(stepImpl);
            }
        }

        WhileCycleStep whileCycleStep = new WhileCycleStep(maxLoopCount, conditionalRelationship, testConditions,
                childrenStep);
        setCommonProperties(whileCycleStep, stepEntity);

        return whileCycleStep;
    }
}
