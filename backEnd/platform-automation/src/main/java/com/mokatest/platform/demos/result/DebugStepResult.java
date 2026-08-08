package com.mokatest.platform.demos.result;

import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author JingLong
 * @Description 调试步骤结果（专用于调试步骤的返回结果。和stepResult差不多，只是少了子步骤）
 * @Date 2025/12/21 20:11
 **/
@Data
public class DebugStepResult implements Serializable {


    private static final long serialVersionUID = 210394799311029159L;
    private AbstractTestStep step;

    private BaseStepResult result;


    // 迭代步骤结果收集（只在循环状态下使用，通常情况下只使用 result）
    private Map<Integer, BaseStepResult> iterations = new LinkedHashMap<>();


    // 是否处于循环中
    private Integer isLoop = 0;


    // 场景ID
    private Integer sceneId;

    public static DebugStepResult build(StepResult stepResult) {
        DebugStepResult debugStepResult = new DebugStepResult();
        BeanUtils.copyProperties(stepResult, debugStepResult);
        return debugStepResult;
    }

}
