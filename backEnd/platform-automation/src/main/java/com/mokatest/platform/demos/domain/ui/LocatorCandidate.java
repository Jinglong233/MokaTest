package com.mokatest.platform.demos.domain.ui;

import lombok.Data;

import java.io.Serializable;

/**
 * 定位候选链中的一项。
 * 链为有序数组 [{type, value, score?}]，第一项即元素的「主定位」，
 * 与 element 表的 locator_type / locator_value 保持一致。
 * type 取值见 {@link com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType}。
 */
@Data
public class LocatorCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 定位类型（ElementLocatorType 名称，大小写不敏感）
     */
    private String type;

    /**
     * 定位值
     */
    private String value;

    /**
     * 候选置信度（录制插件产出，可选）
     */
    private Double score;
}
