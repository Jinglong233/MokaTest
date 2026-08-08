package com.mokatest.platform.demos.domain.ui.dto.step;// for循环操作


import com.mokatest.platform.demos.domain.ui.uiEnum.cycle.ForCycleType;
import lombok.Data;

@Data
public class ForStepDTO extends StepBaseDTO {
    private String stepType;
    // 循环方式
    private ForCycleType cycleType;
    // 循环次数
    private Integer cycleTimes;
    // 循环文件
    private String cycleFile;

}






