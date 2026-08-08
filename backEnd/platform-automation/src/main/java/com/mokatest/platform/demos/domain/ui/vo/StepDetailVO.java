package com.mokatest.platform.demos.domain.ui.vo;

import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.AssertStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.ExtractStepDTO;
import lombok.Data;

import java.util.List;

/**
 * @Author JingLong
 * @Description 步骤详情视图对象
 * @Date 2025/8/6 11:31
 **/
@Data
public class StepDetailVO {
    private TestStep step;
    private List<AssertStepDTO> conditions;
    private List<ExtractStepDTO> extractSteps;
}
