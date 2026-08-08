package com.mokatest.platform.demos.domain.ui.dto.step;

// 切换页签

import com.mokatest.platform.demos.domain.ui.uiEnum.browser.SwitchTabMode;
import lombok.Data;

@Data
public class SwitchTabStepDTO extends StepBaseDTO {
    private String stepType;
    private SwitchTabMode switchTabMode; // 关闭页面模式
    private Integer customIndex; // 自定义关闭页面索引
}