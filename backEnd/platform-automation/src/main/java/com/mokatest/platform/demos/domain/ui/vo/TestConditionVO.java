package com.mokatest.platform.demos.domain.ui.vo;

import com.mokatest.platform.demos.domain.ui.dto.step.AssertStepDTO;
import lombok.Data;

/**
 * @Author JingLong
 * @Description 测试条件视图
 * @Date 2025/8/6 17:14
 **/
@Data
public class TestConditionVO {
    private AssertStepDTO stepCondition;
//    private List<ConditionParameter> conditionParameters;
}
