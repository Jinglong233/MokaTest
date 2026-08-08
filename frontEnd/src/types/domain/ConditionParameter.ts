export interface ConditionParameter {
    id: string; // 条件ID
    condation_id: string; // 关联断言ID
    condition_type: string; // 条件类型
    assertion_type: string; // 断言类型
    comparator: string; // 比较关系
    expected_value: string; // 期望值
    element_id: string; // 关联元素ID
    custom_element_type: string; // 自定义元素类型
    custom_element_value: string; // 自定义元素值
    property_name: string; // 属性名(当断言元素属性时使用)
    order_index: number; // 执行顺序
    created_at: string;
}