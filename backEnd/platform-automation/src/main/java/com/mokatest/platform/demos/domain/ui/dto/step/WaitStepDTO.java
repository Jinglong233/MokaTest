package com.mokatest.platform.demos.domain.ui.dto.step;


import com.mokatest.platform.demos.domain.ui.uiEnum.WaitType;
import lombok.Data;

// 等待操作
@Data
public class WaitStepDTO extends StepBaseDTO {

    private String stepType;
    // todo 考虑做等待类型
    private WaitType waitType;
    private Integer waitTime; // 等待时间
}