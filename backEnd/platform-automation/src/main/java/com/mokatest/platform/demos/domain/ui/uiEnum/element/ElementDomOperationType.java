package com.mokatest.platform.demos.domain.ui.uiEnum.element;

/**
 * @Author JingLong
 * @Description 元素DOM操作类型
 * @Date 2026/7/16
 **/
public enum ElementDomOperationType {
    // 设置属性
    SET_ATTRIBUTE,
    // 移除属性
    REMOVE_ATTRIBUTE,
    // 设置内联样式
    SET_STYLE,
    // 追加CSS类
    ADD_CLASS,
    // 移除CSS类
    REMOVE_CLASS,
    // 触发原生事件
    DISPATCH_EVENT
}
