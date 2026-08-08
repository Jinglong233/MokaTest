package com.mokatest.platform.demos.domain.ui.dto.step;


import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertRelationship;
import com.mokatest.platform.demos.domain.ui.uiEnum.page.PageAttribute;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import lombok.Data;

// 断言操作
@Data
public class AssertStepDTO extends StepBaseDTO {
    private String stepType;
    private String assertType; // 断言类型
    private ElementDTO element; // 断言元素
    private String assertText; // 断言文本
    private String elementAttribute; // 断言元素属性
    private AssertRelationship assertRelationship; // 断言关系
    private String exceptValue; // 期望值
    private PageAttribute pageAttribute; // 断言元素属性
}






