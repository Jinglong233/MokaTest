package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;

public interface StepCreationStrategy {



    /**
     * 是否支持该步骤类型
     *
     * @param stepType 步骤类型
     * @return 是否支持
     */
    boolean supports(String stepType);

    /**
     * 创建可执行步骤
     *
     * @param stepEntity 步骤实体
     * @return 可执行步骤实例
     */
    AbstractTestStep createExecutableStep(TestStep stepEntity);


    /**
     * 获取步骤类型
     * @return
     */
    StepType getStepType();

}