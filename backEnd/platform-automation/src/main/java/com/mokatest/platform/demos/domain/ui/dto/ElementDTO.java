package com.mokatest.platform.demos.domain.ui.dto;

import com.mokatest.platform.demos.domain.ui.Element;
import lombok.Data;

@Data
public class ElementDTO {
    // 选择元素的
    private Element locator;
    // 自定义元素定位的
    private Element customLocator;
    /**
     * 定位来源：LIBRARY=库选元素，CUSTOM=自定义定位。
     * 为空表示历史数据，按「库选优先」规则取值。
     */
    private String locatorSource;
}


