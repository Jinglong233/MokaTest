package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.ConditionalRelationship;
import com.mokatest.platform.demos.condation.TestCondition;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractAssertTestStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 断言步骤
 */
public class AssertStep extends AbstractTestStep implements AbstractAssertTestStep {

    // 目前断言步骤只支持单个条件，且没有条件关系

    // 条件列表
    private List<TestCondition> conditions;


    // 条件关系
    private ConditionalRelationship conditionalRelationship;


    public AssertStep(List<TestCondition> conditions, ConditionalRelationship conditionalRelationship) {
        this.conditions = conditions;
        this.conditionalRelationship = conditionalRelationship;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();
        boolean b = false;
        Map<Integer, AssertResult> evaluationResults = IntStream.range(0, conditions.size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> i + 1,  // key: 执行顺序从1开始
                        i -> conditions.get(i).evaluate(context)
                ));

        b = evaluationResults.values().stream().allMatch(AssertResult::getSuccess);
        if (!b) {
            updateFailedAssertCount(context);
        } else {
            updateSuccessAssertCount(context);
        }
        result.getResult().setAssertResults(evaluationResults);
         context.getCurrentCommonStepResult().setStatus(b ? StepExecutionType.SUCCESS : StepExecutionType.FAILURE);
        if (!b) {
            result.getResult().setErrorMessage(b ? "断言成功" : "断言失败");
        }


        return result;
    }


}