package com.mokatest.platform.demos.domain.ui.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/2 15:08
 **/
@Data
public class ElementVO {
    /**
     * 元素ID
     */
    private Integer id;

    /**
     * 父id
     */
    private Integer parentId;

    /**
     * 元素名称
     */
    private String elementName;

    /**
     * 类型
     */
    private Object elementType;

    /**
     * 定位类型
     */
    private Object locatorType;

    /**
     * 定位值
     */
    private String locatorValue;

    /**
     * 元素描述
     */
    private String description;

    /**
     * 排序
     */
    private Integer sort;

    private List<ElementVO> children;
}
