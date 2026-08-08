package com.mokatest.platform.demos.domain.ui.dto.step;// 关闭页面操作

import com.mokatest.platform.demos.domain.ui.uiEnum.browser.ColsePageMode;
import lombok.Data;

@Data
public class ClosePageStepDTO extends StepBaseDTO {

    private String stepType;
    private ColsePageMode closePageMode; // 关闭页面模式
    private Integer customIndex; // 自定义关闭页面索引
}
