package com.mokatest.platform.demos.domain.ui.dto.queryDto;

import lombok.Data;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/11/5 14:07
 **/
@Data
public class ElementQueryDTO extends BasePageQueryDTO{

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
    private String locatorType;


    /**
     * 元素描述
     */
    private String description;

    /**
     * 所属项目ID
     */
    private String projectId;


    /**
     * 是否共享元素(1-共享，0-私有)
     */
    private Integer isShared;
}
