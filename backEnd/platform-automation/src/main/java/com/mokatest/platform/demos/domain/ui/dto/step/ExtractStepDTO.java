package com.mokatest.platform.demos.domain.ui.dto.step;// 提取操作


import com.mokatest.platform.demos.domain.ui.uiEnum.extract.ElementExtractValueType;
import com.mokatest.platform.demos.domain.ui.uiEnum.extract.ExtractType;
import com.mokatest.platform.demos.domain.ui.uiEnum.extract.PageExtractValueType;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import lombok.Data;

// 提取操作
@Data
public class ExtractStepDTO extends StepBaseDTO {
    private String stepType; // 步骤类型
    private String variableName; // 变量名
    private ExtractType extractType; // 提取类型
    private ElementDTO element; // 提取的目标元素
    private ElementExtractValueType elementExtractType; // 元素提取类型
    private String elementAttribute;
    private PageExtractValueType pageExtractType; // 页面提取类型

    private String cookieName; // cookie名称
}

