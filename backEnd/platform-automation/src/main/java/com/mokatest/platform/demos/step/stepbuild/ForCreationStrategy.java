package com.mokatest.platform.demos.step.stepbuild;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.ForStepDTO;
import com.mokatest.platform.demos.mapper.TestStepMapper;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.ForStep;
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
 * @Description
 * @Date 2025/8/30 17:08
 **/
@Component
public class ForCreationStrategy extends StepAbstractBuilder {


    @Resource
    private TestStepMapper testStepMapper;

    private final StepBuilderFactory factory;


    // 使用@Lazy打破循环
    @Autowired
    public ForCreationStrategy(@Lazy StepBuilderFactory stepBuilderFactory) {
        super(StepType.FOR);
        this.factory = stepBuilderFactory;
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.FOR.toString().equals(stepType);
    }


    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        ForStepDTO forStepDTO = gson.fromJson(stepDetail.toString(), ForStepDTO.class);


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
        ForStep forStep = new ForStep(forStepDTO.getCycleTimes(), childrenStep);
        setCommonProperties(forStep, stepEntity);
        return forStep;
    }
}
