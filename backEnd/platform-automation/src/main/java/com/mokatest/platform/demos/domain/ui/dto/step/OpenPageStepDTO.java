package com.mokatest.platform.demos.domain.ui.dto.step;// 打开页面操作

import lombok.Data;

@Data
public class OpenPageStepDTO extends StepBaseDTO {
    private String stepType; // 步骤类型
    private String url; // 页面地址
    private Integer recover;// 是否覆盖输入

}
