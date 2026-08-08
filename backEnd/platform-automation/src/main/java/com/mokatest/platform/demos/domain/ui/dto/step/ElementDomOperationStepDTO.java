package com.mokatest.platform.demos.domain.ui.dto.step;

import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementDomOperationType;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.StylePriority;
import lombok.Data;

import java.util.List;

/**
 * @Author JingLong
 * @Description 元素DOM操作步骤
 * @Date 2026/7/16
 **/
@Data
public class ElementDomOperationStepDTO extends StepBaseDTO {
    private String stepType; // 步骤类型
    private ElementDomOperationType operationType; // 操作类型
    private ElementDTO element; // 目标元素

    // 属性操作
    private String attributeName; // 属性名
    private String attributeValue; // 属性值（空字符串也允许，支持 {{变量名}}）

    // 样式操作
    private String styleName; // CSS属性名
    private String styleValue; // CSS属性值（支持 {{变量名}}）
    private StylePriority stylePriority; // 样式优先级

    // 类名操作
    private List<String> classNames; // CSS类名列表

    // 事件操作
    private String eventType; // 事件类型
    private Boolean eventBubbles; // 是否冒泡
    private Boolean eventCancelable; // 是否可取消
}
