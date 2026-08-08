package com.mokatest.platform.demos.domain.ui.dto.step;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 脚本步骤（在场景变量池上下文中执行 JS）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScriptStepDTO extends StepBaseDTO {

    private String stepType;

    /**
     * JS 脚本内容（可读写 context 变量、console.log、context.assertCondition）
     */
    private String scriptContent;
}
