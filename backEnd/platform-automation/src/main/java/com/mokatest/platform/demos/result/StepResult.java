package com.mokatest.platform.demos.result;

import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 测试步骤结果

@Data
public class StepResult implements Serializable {

    private static final long serialVersionUID = 256540786291796166L;

    private AbstractTestStep step;

    private BaseStepResult result;


    // 迭代步骤结果收集（只在循环状态下使用，通常情况下只使用 result）
    private Map<Integer, BaseStepResult> iterations = new LinkedHashMap<>();


    // 子步骤结果
    private List<StepResult> children;


    // 是否属于迭代步骤
//    private boolean isIteration;

    // 是否处于循环中
    private Integer isLoop = 0;


    // 场景ID
    private Integer sceneId;


    public StepResult(AbstractTestStep step) {
        this.step = step;
    }


}