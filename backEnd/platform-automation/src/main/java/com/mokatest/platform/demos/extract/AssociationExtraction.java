package com.mokatest.platform.demos.extract;

import com.mokatest.platform.demos.domain.ui.uiEnum.extract.ExtractType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.dto.step.ExtractStepDTO;

/**
 * @Author JingLong
 * @Description 关联提取
 * @Date 2025/7/21 20:32
 **/
public interface AssociationExtraction {
    Object extract(TestExecutionContext context, ExtractStepDTO extractorInfo);

    boolean isSupport(ExtractType extractType);
}
