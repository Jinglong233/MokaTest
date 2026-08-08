// 元素DOM操作类型
export enum ElementDomOperationType {
    // 设置属性
    SET_ATTRIBUTE = "SET_ATTRIBUTE",
    // 移除属性
    REMOVE_ATTRIBUTE = "REMOVE_ATTRIBUTE",
    // 设置内联样式
    SET_STYLE = "SET_STYLE",
    // 追加CSS类
    ADD_CLASS = "ADD_CLASS",
    // 移除CSS类
    REMOVE_CLASS = "REMOVE_CLASS",
    // 触发原生事件
    DISPATCH_EVENT = "DISPATCH_EVENT",
}
