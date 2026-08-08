package com.mokatest.platform.demos.step.abstractStep;


import com.mokatest.platform.demos.config.DebugWebSocketHandler;
import com.mokatest.platform.demos.debug.PlaywrightDebugSession;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.result.DebugStepResult;
import com.mokatest.platform.demos.result.StepResult;
import com.google.gson.Gson;

import java.util.List;

/**
 * @Author JingLong
 * @Description 子步骤操作类
 * @Date 2025/10/14 16:53
 **/
public abstract class AbstractChildrenStep extends AbstractTestStep {

    private static final Gson GSON = new Gson();

    public void exectuionChildrenStep(TestExecutionContext context, List<AbstractTestStep> childrenSteps) {
        exectuionParentStep(context);
        if (childrenSteps != null && !childrenSteps.isEmpty()) {
            for (AbstractTestStep childrenStep : childrenSteps) {
                // 判断步骤是否被禁用
                if (childrenStep.getIsDisable() == 1) continue;
                if (context.contextIsContinue()) {
                    // 只有符合继续执行条件，才往下继续执行
                    StepResult childResult = childrenStep.execute(context);
                    // 子步骤失败聚合到容器：容器标记 FAILURE，顶层 executeTest 才能按异常策略挂起/中断。
                    // 异常策略=停止时子步骤已把 isContinue 置 false，后续子步骤不再执行；
                    // 策略=继续时 isContinue 保持 true，兄弟步骤继续跑，仅容器最终状态为失败
                    if (isFailureResult(childResult)) {
                        context.getCurrentCommonStepResult().setStatus(StepExecutionType.FAILURE);
                    }
                }
            }
        }
    }

    /**
     * 判断子步骤结果是否失败（与 executeTest 的判定口径一致：普通步骤看 result，循环步骤看最后一次迭代）
     */
    private boolean isFailureResult(StepResult result) {
        if (result == null) {
            return false;
        }
        if (result.getIterations() == null || result.getIterations().isEmpty()) {
            return result.getResult() != null
                    && result.getResult().getStatus() == StepExecutionType.FAILURE;
        }
        com.mokatest.platform.demos.result.BaseStepResult last =
                result.getIterations().get(result.getIterations().size());
        return last != null && last.getStatus() == StepExecutionType.FAILURE;
    }

    /**
     * 回传处理父步骤操作结果
     *
     * @param context
     */
    private void exectuionParentStep(TestExecutionContext context) {
        if (context instanceof PlaywrightDebugSession) {
            stepScreenshot(context);
            // 队列不为空的时候，才进行回传
            if (context.getCompletedResultQueue().size() > 0) {
                // 父步骤在子步骤列表执行前每一次执行都得回传一次结果
                DebugWebSocketHandler.sendToSession(context.getSessionId(),
                        GSON.toJson(DebugStepResult.build(context.getCurrentStepResult())));
            }
        }
    }


}
