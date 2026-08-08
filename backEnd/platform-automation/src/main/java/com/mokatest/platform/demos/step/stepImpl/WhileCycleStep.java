package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.ConditionalRelationship;
import com.mokatest.platform.demos.condation.TestCondition;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractAssertTestStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractChildrenStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractLoopTestStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/7/29 17:40
 **/
public class WhileCycleStep extends AbstractChildrenStep implements AbstractAssertTestStep, AbstractLoopTestStep {

    /**
     * 最大循环次数
     */
    private Integer maxLoopCount;

    /**
     * 条件关系
     */
    private ConditionalRelationship relationship;

    /**
     * 条件列表
     */
    private List<TestCondition> testConditions;

    private List<AbstractTestStep> childrenSteps;


    public WhileCycleStep(Integer maxLoopCount, ConditionalRelationship relationship,
                          List<TestCondition> testConditions) {
        this.testConditions = testConditions;
        this.relationship = relationship;
        this.maxLoopCount = maxLoopCount;
    }


    public WhileCycleStep(Integer maxLoopCount, ConditionalRelationship relationship,
                          List<TestCondition> testConditions, List<AbstractTestStep> childrenSteps) {
        this.testConditions = testConditions;
        this.relationship = relationship;
        this.maxLoopCount = maxLoopCount;
        this.childrenSteps = childrenSteps;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        if (testConditions.isEmpty()) {
            context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
            return result;
        }

        // 存每次循环的断言结果集合
        Map<Integer, Map<Integer, AssertResult>> assertResults = new HashMap<>();

        // 备注：while循环不统计断言数据量

        // 使用while循环进行操作
        int cycleCount = 0;
        // 执行子步骤
        if (childrenSteps != null && !childrenSteps.isEmpty()) {
            try {
                while (true) {
                    // 判断是否超过最大循环次数
                    if (maxLoopCount != null && maxLoopCount > 0) {
                        if (cycleCount >= maxLoopCount) {
                            context.getCurrentCommonStepResult().setErrorMessage(String.format("超过最大循环次数：%s次",
                                    maxLoopCount));
                            break;
                        }
                    }
                    // 判断是否允许继续执行 。 备注：这里判断是考虑到，当执行子步骤时，子步骤失败，那后续的循环也就不需要执行了
                    if (!context.contextIsContinue()) {
                        break;
                    }

                    // 增加循环计数并在执行判断
                    if (!calculateJudgmentResult(context, assertResults, ++cycleCount)) {
                        break;
                    }

                    exectuionChildrenStep(context, childrenSteps);

                }
            } catch (Exception e) {
                context.getCurrentCommonStepResult().setErrorMessage(String.format("执行异常：%s",
                        e.getMessage()));
                throw new RuntimeException(e);
            }
        }
        // 条件不成立退出循环 / 循环正常结束均为成功；但循环体内子步骤失败已被聚合为 FAILURE 时保留失败状态，
        // 让顶层 executeTest 能按异常策略挂起/中断
        if (context.getCurrentCommonStepResult().getStatus() != StepExecutionType.FAILURE) {
            context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        }
        context.getCurrentCommonStepResult().setWhileAssertResults(assertResults);
        return result;
    }

    /**
     * 计算判断结果
     *
     * @param context       上下文
     * @param assertResults 断言结果列表
     * @return
     */
    private boolean calculateJudgmentResult(TestExecutionContext context,
                                            Map<Integer, Map<Integer, AssertResult>> assertResults,
                                            Integer cycleCount) {
        boolean b = false;
        // 先计算测试结果
        Map<Integer, AssertResult> evaluationResults =
                IntStream.range(0, testConditions.size()).boxed().collect(Collectors.toMap(i -> i + 1,  // key: 执行顺序从1开始
                        i -> testConditions.get(i).evaluate(context)));
        assertResults.put(cycleCount, evaluationResults);
        if (ConditionalRelationship.AND.equals(relationship)) {
            b = evaluationResults.values().stream().allMatch(AssertResult::getSuccess);
        } else if (ConditionalRelationship.OR.equals(relationship)) {
            b = evaluationResults.values().stream().anyMatch(AssertResult::getSuccess);
        } else {
            b = evaluationResults.values().stream().allMatch(AssertResult::getSuccess);
        }
        // 注意：每一次都得重置断言结果
        context.getCurrentCommonStepResult().setWhileAssertResults(assertResults);
        return b;
    }

}
