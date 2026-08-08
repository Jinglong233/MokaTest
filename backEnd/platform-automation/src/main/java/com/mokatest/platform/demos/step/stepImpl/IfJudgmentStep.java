package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.ConditionalRelationship;
import com.mokatest.platform.demos.condation.TestCondition;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractAssertTestStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractChildrenStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @Author JingLong
 * @Description if判断步骤
 * @Date 2025/7/23 20:41
 **/
@Data
public class IfJudgmentStep extends AbstractChildrenStep implements AbstractAssertTestStep {


    private List<TestCondition> testConditions;

    private ConditionalRelationship operator;

    private List<AbstractTestStep> childrenSteps;


    public IfJudgmentStep(List<TestCondition> testConditions, ConditionalRelationship operator) {
        this.operator = operator;
        this.testConditions = testConditions;
    }


    public IfJudgmentStep(List<TestCondition> testConditions, ConditionalRelationship operator,
                          List<AbstractTestStep> childrenSteps) {
        this.operator = operator;
        this.testConditions = testConditions;
        this.childrenSteps = childrenSteps;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();
        if (testConditions.isEmpty()) {
             context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        }

        boolean b = false;
        Map<Integer, AssertResult> evaluationResults = IntStream.range(0, testConditions.size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> i + 1,  // key: 执行顺序从1开始
                        i -> testConditions.get(i).evaluate(context)
                ));
        if (ConditionalRelationship.AND.equals(operator)) {
            b = evaluationResults.values().stream().allMatch(AssertResult::getSuccess);
        } else if (ConditionalRelationship.OR.equals(operator)) {
            b = evaluationResults.values().stream().anyMatch(AssertResult::getSuccess);
        } else {
            b = evaluationResults.values().stream().allMatch(AssertResult::getSuccess);
        }
        context.getCurrentCommonStepResult().setAssertResults(evaluationResults);

        if (b) {
            // 条件成立：执行子步骤。子步骤失败会由 exectuionChildrenStep 聚合为容器 FAILURE，
            // 顶层按异常策略挂起/中断
            updateSuccessAssertCount(context);
            context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
            exectuionChildrenStep(context, childrenSteps);
        } else {
            // 条件不成立 = 分支未命中，跳过子步骤。这是正常的控制流分支，不是失败：
            // 标记 SKIPPED，避免异常策略=停止时把「元素不存在就跳过」这类用法误判为失败挂起
            context.getCurrentCommonStepResult().setStatus(StepExecutionType.SKIPPED);
        }
        return result;
    }


}
