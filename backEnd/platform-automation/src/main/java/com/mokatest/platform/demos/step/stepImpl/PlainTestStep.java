package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;

/**
 * 仅用于报告序列化的空步骤
 *
 * API 计划执行后，需要把 {@link com.mokatest.platform.demos.api.service.ApiSceneDebugService.ApiStepResult}
 * 转换为 {@link com.mokatest.platform.demos.result.StepResult} 写入报告。</p>
 *
 * {@link com.mokatest.platform.demos.step.abstractStep.AbstractTestStep} 是抽象类，不能直接实例化，
 * 因此提供本类作为占位步骤，不执行任何实际逻辑。</p>
 */
public class PlainTestStep extends AbstractTestStep {

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        return null;
    }
}
