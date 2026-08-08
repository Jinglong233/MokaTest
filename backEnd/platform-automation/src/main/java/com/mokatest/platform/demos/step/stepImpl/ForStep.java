package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractChildrenStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractLoopTestStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import lombok.Data;

import java.util.List;

/**
 * @Author JingLong
 * @Description 循环步骤
 * @Date 2025/8/30 17:07
 **/
@Data
public class ForStep extends AbstractChildrenStep implements AbstractLoopTestStep {
    // 循环次数
    private Integer cycleNumbers;

    private List<AbstractTestStep> childrenStep;


    public ForStep(Integer cycleNumbers, List<AbstractTestStep> childrenStep) {
        this.cycleNumbers = cycleNumbers;
        this.childrenStep = childrenStep;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        if (childrenStep == null || childrenStep.isEmpty()) {
            context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
            return result;
        }
        try {
            for (int i = 0; i < cycleNumbers; i++) {
                // 判断是否允许继续执行 。 备注：这里判断是考虑到，当执行子步骤时，子步骤失败，那后续的循环也就不需要执行了
                if (!context.contextIsContinue()) {
                    break;
                }

                exectuionChildrenStep(context, childrenStep);


            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 循环正常结束为成功；循环体内子步骤失败已被聚合为 FAILURE 时保留失败状态，
        // 让顶层 executeTest 能按异常策略挂起/中断
        if (context.getCurrentCommonStepResult().getStatus() != StepExecutionType.FAILURE) {
            context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        }

        return result;
    }
}